#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

// Group number: 09
// Group members: Ernesto Natuzzi, Flavia Nicotri, Luca Pagano

const double L = 100.0;                 // Length of the 1d domain
const int n = 1000;                     // Total number of points
const int iterations_per_round = 1000;  // Number of iterations for each round of simulation
const double allowed_diff = 0.001;     // Stopping condition: maximum allowed difference between values

/**
 * @brief Initial Condition
 * 
 * @param x input the position of the point (x)
 * @param L length of the domain
 * @return double 
 */
double initial_condition(double x, double L) {
    return fabs(x - L / 2);
}

/**
 * @brief Find the minimum value in the timeline
 * 
 * @param timeline the array to search
 * @param n its dimension
 * @return double 
 */
double find_min(double *timeline, int n) {
    double min = timeline[0];
    for (int i = 1; i < n; i++) {
        if (timeline[i] < min) {
            min = timeline[i];
        }
    }
    return min;
}

/**
 * @brief Find the maximum value in the timeline
 * 
 * @param timeline the array to search
 * @param n its dimension
 * @return double 
 */
double find_max(double *timeline, int n) {
    double max = timeline[0];
    for (int i = 1; i < n; i++) {
        if (timeline[i] > max) {
            max = timeline[i];
        }
    }
    return max;
}

int main(int argc, char **argv) {
    MPI_Init(&argc, &argv);

    int rank, size;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    // Variables declaration and initialization
    int local_n = n / size;
    double* local_timeline = (double *)malloc(local_n * sizeof(double));
    double *timeline;

    if (rank == 0) {
        // Master node
        timeline = (double *)malloc(n * sizeof(double));
        for (int i = 0; i < n; i++) {
            timeline[i] = initial_condition(i * L / (n-1), L);
        }
    }

    MPI_Scatter(timeline, local_n, MPI_DOUBLE, local_timeline, local_n, MPI_DOUBLE, 0, MPI_COMM_WORLD);

    if (rank == 0){
        free(timeline);
    }

    double* new_local_timeline = (double *)malloc(local_n * sizeof(double));

    int round = 0;
    while (1) {
        double min, max;
        double* tmp;
        double prev, next;
        // Perform one round of iterations
        round++;
        // Inner points
        for (int t = 0; t < iterations_per_round; t++) {

            for (int i = 1; i < local_n - 1; i++) {
                new_local_timeline[i] = (local_timeline[i - 1] + local_timeline[i] + local_timeline[i + 1]) / 3;
            }

            // Edge cases 0 and n-1
            if (rank == 0) {
                new_local_timeline[0] = (local_timeline[0] + local_timeline[1]) / 2;
            }
            if (rank == size - 1) {
                new_local_timeline[local_n - 1] = (local_timeline[local_n - 2] + local_timeline[local_n - 1]) / 2;
            }

            // FORWARD: Passing local_n-1 to rank+1
            if (rank < size - 1) {
                MPI_Send(&local_timeline[local_n - 1], 1, MPI_DOUBLE, rank + 1, 0, MPI_COMM_WORLD);
            }

            // Receives values of -1 from rank-1
            if (rank > 0) {
                MPI_Recv(&prev, 1, MPI_DOUBLE, rank - 1, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
                // BACKWARD: Passing 0 to rank-1
                MPI_Send(&local_timeline[0], 1, MPI_DOUBLE, rank - 1, 0, MPI_COMM_WORLD);
            }

            // Receives values of local_n+1 from rank+1
            if (rank < size - 1) {
                MPI_Recv(&next, 1, MPI_DOUBLE, rank + 1, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
            }

            // We are assuming that the size of the local_timeline is greater or equal than 2
            // Update the edge points where they are interconnected
            if (rank > 0) {
                new_local_timeline[0] = (prev + local_timeline[0] + local_timeline[1]) / 3;
            }
            if (rank < size - 1) {
                new_local_timeline[local_n - 1] = (local_timeline[local_n - 2] + local_timeline[local_n - 1] + next) / 3;
            }

            tmp = local_timeline;
            local_timeline = new_local_timeline;
            new_local_timeline = tmp;
        }

        min = find_min(local_timeline, local_n);
        max = find_max(local_timeline, local_n);

        // Compute global minimum and maximum
        double global_min, global_max, max_diff;
        MPI_Allreduce(&min, &global_min, 1, MPI_DOUBLE, MPI_MIN, MPI_COMM_WORLD);
        MPI_Allreduce(&max, &global_max, 1, MPI_DOUBLE, MPI_MAX, MPI_COMM_WORLD);
        
        // Compute the maximum difference between the local timelines
        max_diff = global_max - global_min;

        if (rank == 0) {
            printf("Round: %d\tMin: %f.5\tMax: %f.5\tDiff: %f.5\n", round, global_min, global_max, max_diff);
        }

        // Break the loop if the difference is less than the allowed difference
        if (max_diff < allowed_diff) {
            if (rank == 0){
                printf("Converged after %d rounds\n", round);
            }
            break;
        }
    }

    // Deallocation
    free(local_timeline);
    free(new_local_timeline);

    MPI_Finalize();
    return 0;
}
