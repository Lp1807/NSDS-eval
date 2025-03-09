#include "contiki.h"
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "net/ipv6/simple-udp.h"

#include "sys/log.h"
#include "random.h"

#define LOG_MODULE "App"
#define LOG_LEVEL LOG_LEVEL_INFO

#define UDP_CLIENT_PORT	8765
#define UDP_SERVER_PORT	5678
#define MAX_RECEIVERS 10
#define SEND_INTERVAL	(60 * CLOCK_SECOND)
#define MAX_MISSING 3

static struct simple_udp_connection udp_conn;


static uip_ipaddr_t nodes[MAX_RECEIVERS];
static uip_ipaddr_t parents[MAX_RECEIVERS];
static int num_missing[MAX_RECEIVERS];
static bool is_alive[MAX_RECEIVERS];
static int num_clients = 0;
static bool message_arrived[MAX_RECEIVERS];

PROCESS(udp_server_process, "UDP server");
AUTOSTART_PROCESSES(&udp_server_process);
/*---------------------------------------------------------------------------*/
static void
udp_rx_callback(struct simple_udp_connection *c,
         const uip_ipaddr_t *sender_addr,
         uint16_t sender_port,
         const uip_ipaddr_t *receiver_addr,
         uint16_t receiver_port,
         const uint8_t *data,
         uint16_t datalen)
{ 
  // take the addres of the parent in data
  uip_ipaddr_t parent = *(uip_ipaddr_t*)data;
  int index_first_missing = -1;
  int found = 0;
  for (int i=0; i<num_clients; i++) {
    if (uip_ipaddr_cmp(sender_addr, &nodes[i])) {
      found = 1;
      num_missing[i] = 0;
      message_arrived[i] = true;
      is_alive[i] = true;

      if (!uip_ipaddr_cmp(&parents[i], &parent)) {
        uip_ipaddr_copy(&parents[i], &parent);
        LOG_INFO("Parent updated for client: ");
        LOG_INFO_6ADDR(sender_addr);
        LOG_INFO_("\n");
      }
      else {
        LOG_INFO("Parent not updated for client: ");
        LOG_INFO_6ADDR(sender_addr);
        LOG_INFO_("\n");
      }
      break;
    }

    if (is_alive[i] == false && index_first_missing == -1) {
      //save the index of the client that is missing
      index_first_missing = i;
      LOG_INFO("Client ");
      LOG_INFO_6ADDR(&nodes[i]);
      LOG_INFO_(" is being attentioned\n");
    }

  }
  if (!found) {
    if (num_clients < MAX_RECEIVERS) {
      uip_ipaddr_copy(&nodes[num_clients], sender_addr);
      uip_ipaddr_copy(&parents[num_clients], &parent);
      num_missing[num_clients] = 0;
      message_arrived[num_clients] = true;
      is_alive[num_clients] = true;
      num_clients++;
      LOG_INFO("New client registered: ");
      LOG_INFO_6ADDR(sender_addr);
      LOG_INFO_("\n");
    }
    else if (index_first_missing != -1) {
      uip_ipaddr_copy(&nodes[index_first_missing], sender_addr);
      uip_ipaddr_copy(&parents[index_first_missing], &parent);
      num_missing[index_first_missing] = 0;
      message_arrived[index_first_missing] = true;
      is_alive[index_first_missing] = true;
      LOG_INFO("Client ");
      LOG_INFO_6ADDR(sender_addr);
      LOG_INFO_(" is being placed in index %d\n", index_first_missing);
    }
    else {
      LOG_INFO("Client ");
      LOG_INFO_6ADDR(sender_addr);
      LOG_INFO_("not registered and no space left\n");
    }
  }
}
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(udp_server_process, ev, data)
{
  PROCESS_BEGIN();

  static struct etimer periodic_timer;

  /* Initialize temperature buffer */
  for (int i=0; i<MAX_RECEIVERS; i++) {
    num_missing[i] = 0;
    message_arrived[i] = false;
  }

  /* Initialize DAG root */
  NETSTACK_ROUTING.root_start();

  /* Initialize UDP connection */
  simple_udp_register(&udp_conn, UDP_SERVER_PORT, NULL,
                      UDP_CLIENT_PORT, udp_rx_callback);

  etimer_set(&periodic_timer, SEND_INTERVAL);
  while(1) {
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&periodic_timer));

    for (int i=0; i<num_clients; i++) {
      if (!message_arrived[i]) {
        num_missing[i]++;
        if (num_missing[i] >= MAX_MISSING) {
          is_alive[i] = false;
          LOG_INFO("Client ");
          LOG_INFO_6ADDR(&nodes[i]);
          LOG_INFO_(" is missing\n");
        }
      }
      message_arrived[i] = false;
    }

    // display the information of the clients
    for (int i=0; i<num_clients; i++) {
      if (is_alive[i]) {
        LOG_INFO("Client ");
        LOG_INFO_6ADDR(&nodes[i]);
        LOG_INFO_(" is connected to ");
        LOG_INFO_6ADDR(&parents[i]);
        LOG_INFO_("\n");
      }
    }

    etimer_set(&periodic_timer, SEND_INTERVAL);
  }
  
  PROCESS_END();
}
/*---------------------------------------------------------------------------*/
