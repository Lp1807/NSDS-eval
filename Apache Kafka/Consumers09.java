package it.polimi.nsds.kafka.eval;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Group number: 9
// Group members: Ernesto Natuzzi, Flavia Nicotri, Luca Pagano

// Number of partitions for inputTopic (1, 1000): We have at most 1000 partitions since they keys are at most 1000 and Kafka
// assigns the same partition for each key.

// Number of partitions for outputTopic1 (1, 1): We have only one partition since we the key is always "sum"
// and Kafka assigns the same partition for the reason explained above.

// Number of partitions for outputTopic2 (1, 1000): since we are forwarding the same keys from inputTopic to outputTopic2,
// we need to have a number of partitions equal to the ones of inputTopic.

// Number of instances of Consumer1 (and groupId of each instance) (1, 1): we must have 1 instance of Consumer1 per each groupID
// because otherwise we would not respect the EOS semantics. It is not a problem to have multiple groups. Further explained below.

// Number of instances of Consumer2 (and groupId of each instance) (1, 1000): explained below since strictly related to the number of partitions of inputTopic.

// Please, specify below any relation between the number of partitions for the topics
// and the number of instances of each Consumer

// Consumer 1: The number of partitions for inputTopic is strictly related to the number of instances of Consumer1. As said above,
// to guarantee EOS, a Consumer1 instance has to read all partitions of inputTopic. Therefore, the number of instances of Consumer1
// per group must be exactly one.

// Consumer2: The number of partitions for inputTopic is strictly related to the number of instances of Consumer2.
// Since we are forwarding the same keys from inputTopic to outputTopic2, we need to have a number of instances of Consumer2
// less or equal to the ones of inputTopic, because Kafka puts the same keys in the same partitions. Putting more instances
// will mean that some Consumer2 instances will be in IDLE.

public class Consumers09 {
    public static void main(String[] args) {
        String serverAddr = "localhost:9092";
        int consumerId = Integer.valueOf(args[0]);
        String groupId = args[1];
        if (consumerId == 1) {
            Consumer1 consumer = new Consumer1(serverAddr, groupId);
            consumer.execute();
        } else if (consumerId == 2) {
            Consumer2 consumer = new Consumer2(serverAddr, groupId);
            consumer.execute();
        }
    }

    private static class Consumer1 {
        private final String serverAddr;
        private final String consumerGroupId;

        private static final String inputTopic = "inputTopic";
        private static final String outputTopic = "outputTopic1";
        private static final String producerTransactionalId = "forwarderTransactionalId";
        private static final Integer commitEvery = 10;

        public Consumer1(String serverAddr, String consumerGroupId) {
            this.serverAddr = serverAddr;
            this.consumerGroupId = consumerGroupId;
        }

        public void execute() {
            // Consumer
            final Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            consumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class.getName());

            KafkaConsumer<String, Integer> consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Collections.singletonList(inputTopic));

            final Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
            producerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, producerTransactionalId);
            producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, String.valueOf(true));


            final KafkaProducer<String, Integer> producer = new KafkaProducer<>(producerProps);

            producer.initTransactions();
            List<ConsumerRecord<String, Integer>> processedRecords = new ArrayList<>();

            while (true) {
                final ConsumerRecords<String, Integer> records = consumer.poll(Duration.of(5, ChronoUnit.MINUTES));
                for (final ConsumerRecord<String, Integer> record : records) {
                    processedRecords.add(record);
                    System.out.println("Message with value: " + record.value());
                }

                while (processedRecords.size() >= commitEvery) {
                    int sum = 0;
                    for (int i = 0; i < 10; i++) {
                        sum += processedRecords.get(i).value();
                    }
                    producer.beginTransaction();
                    producer.send(new ProducerRecord<>(outputTopic, "sum", sum));
                    System.out.println("Sent sum: " + sum);
                    final Map<TopicPartition, OffsetAndMetadata> map = new HashMap<>();
                    for (ConsumerRecord<String, Integer> record : processedRecords) {
                        final TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                        final long lastOffset = record.offset();
                        map.put(partition, new OffsetAndMetadata(lastOffset + 1));
                    }
                    processedRecords.subList(0, 10).clear();
                    producer.sendOffsetsToTransaction(map, consumer.groupMetadata());
                    producer.commitTransaction();
                }

            }
        }
    }

    private static class Consumer2 {
        private final String serverAddr;
        private final String consumerGroupId;

        private static final String inputTopic = "inputTopic";
        private static final String outputTopic = "outputTopic2";
        private static final Integer sumEvery = 10;

        public Consumer2(String serverAddr, String consumerGroupId) {
            this.serverAddr = serverAddr;
            this.consumerGroupId = consumerGroupId;
        }

        public void execute() {
            // Consumer
            final Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);

            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class.getName());

            KafkaConsumer<String, Integer> consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Collections.singletonList(inputTopic));

            final Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());

            final KafkaProducer<String, Integer> producer = new KafkaProducer<>(producerProps);

            Map<String, List<Integer>> keyMapValues = new HashMap<>();

            while (true) {
                final ConsumerRecords<String, Integer> records = consumer.poll(Duration.of(5, ChronoUnit.MINUTES));
                for (final ConsumerRecord<String, Integer> record : records) {
                    keyMapValues.putIfAbsent(record.key(), new ArrayList<>());
                    keyMapValues.get(record.key()).add(record.value());
                    System.out.println("Received value: " + record.value() + " for key: " + record.key());
                    if (keyMapValues.get(record.key()).size() == sumEvery) {
                        int sum = keyMapValues.get(record.key()).stream().mapToInt(Integer::intValue).sum();
                        producer.send(new ProducerRecord<>(outputTopic, record.key(), sum));
                        System.out.println("Sent sum: " + sum + " for key: " + record.key());
                        keyMapValues.get(record.key()).clear();
                    }
                }
            }
        }
    }
}