package it.polimi.nsds.eval;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;

import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

// Group number:
// Group members:

public class WebAccessAnalysisGroup09 {
    private static final int NUM_PAGES = 100;

    public static void main(String[] args) throws StreamingQueryException, TimeoutException {
        final String master = args.length > 0 ? args[0] : "local[4]";
        final String filePath = args.length > 1 ? args[1] : "./";
        final String appName = "Web Access Analysis";

        final SparkSession spark = SparkSession
                .builder()
                .master(master)
                .appName(appName)
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");

        // Load batch csv file
        Dataset<Row> accessLog = spark.read().format("csv")
                .option("header", "true")
                .option("inferSchema", "true")
                .load(filePath + "input/web_access_log.csv");

        // Create DataFrame from a rate source.
        // A rate source generates records with a "timestamp" and a "value" column produced at a given rate.
        // We modify the value to be a random number from 1 to NUM_PAGES.
        Dataset<Row> streamingData = spark
                .readStream()
                .format("rate")
                .option("rowsPerSecond", 100)
                .load();
        streamingData = streamingData.withColumn("value", expr("cast(rand() * " + NUM_PAGES + " as int) + 1"));
        streamingData.withWatermark("timestamp", "10 minutes");

        // Query 1: Top 5 most popular pages
        Dataset<Row> popularPages = accessLog.groupBy("PageURL")
                .count()
                .withColumnRenamed("count", "Accesses")
                .orderBy(desc("count"))
                .limit(5);

        popularPages.cache();
        popularPages.show();

        // Query 2: Number of accesses to the top 5 most popular pages for each user.
        Dataset<Row> accessesPerUser = accessLog
                .join(popularPages, "PageURL")
                .groupBy("UserID", "PageURL")
                .count()
                .orderBy("UserID", "PageURL");

        // I ordered by user to get a better visualization of the results per user
        accessesPerUser.show();

        // Query 3: Count of visits to pages in a window of size 10 seconds, slide 4 seconds
        Dataset<Row> windowedCounts = popularPages
                .join(streamingData, popularPages.col("PageURL").equalTo(streamingData.col("value")))
                .groupBy(
                        col("PageURL"),
                        window(col("timestamp"), "10 seconds", "4 seconds")
                )
                .count();

        // Using option truncate = false to correctly see the fields of the window
        StreamingQuery query3 = windowedCounts
                .writeStream()
                .outputMode("update")
                .format("console")
                .option("truncate", "false")
                .start();

        // Query 4: difference between the number of visits in the batch file
        // and the number of visits in each window, for each page

        Dataset<Row> comparison = popularPages
                .join(windowedCounts, "PageURL")
                .withColumn("Difference AccessLog and Window", col("Accesses").minus(col("count")))
                .drop("count", "Accesses");

        StreamingQuery query4 = comparison
                .writeStream()
                .outputMode("update")
                .format("console")
                .option("truncate", "false")
                .start();

        query3.awaitTermination();
        query4.awaitTermination();

        spark.close();
    }
}

