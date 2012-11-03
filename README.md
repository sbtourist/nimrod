# Nimrod 0.6 (WORK-IN-PROGRESS)

Nimrod is a metrics server purely based on log processing: hence, it doesn't affect the way you write your applications, nor has it any side effect on them.
In other words, for those of you who love the bullet points:

* You don't have to import any libraries: just print some logs in a way that Nimrod is able to process, and run the Nimrod metrics server to process them.
* You can use Nimrod regardless of your programming language of choice.
* Nimrod bugs (yes, any piece of software will sooner or later have bugs) will never affect your application.

It currently provides the following features:

* Continuous log processing and metrics extraction.
* Different types of metrics, time-series history with statistical computations, (optional) random sampling.
* Web-based, Javascript-friendly JSON-over-HTTP server, with basic support for Cross Origin Resource Sharing on GET requests.

# Metrics

## Logging

Nimrod metrics are printed on log files *by the user application*, while the Nimrod metrics server only listens to log and processes them: so,
metrics production is completely decoupled from metrics processing and storage.

Metrics must be printed in a Nimrod-specific format, providing the following information in square brackets:

* The **nimrod** identification string.
* The metric **timestamp**, providing *current unix time in milliseconds*.
* The metric **type**, among one of:
 * *alert*
 * *gauge*
 * *counter*
 * *timer*
* The metric **identifier** for the specified type.
* The metric **value**.
* An optional comma-separated list of metric **tags** defining custom information.

Here's an example, without tags:

    [nimrod][123456789][counter][players][100]
    
And with tags:

    [nimrod][123456789][counter][players][100][game_code:123,game_name:poker]

But you can also interleave whatever you want between Nimrod-specific values, in order to make your logs more human-friendly:

    [nimrod][123456789][counter] - Current number of [players] is: [100]

## Alerts

String values representing a generic message at a given time.

Here's a log line representing an alert value:

    [nimrod][123456789][alert][top_player][sergio]

The Nimrod metrics server also computes and provides the following statistical information:

* Mean and variance of the elapsed time.

## Gauges

Number values representing a fixed indicator at a given time.

Here's a log line representing a gauge value:

    [nimrod][123456789][gauge][current_players][100]

The Nimrod metrics server also computes and provides the following statistical information:

* Mean and variance of time intervals between measure updates.
* Mean and variance of the measure.

## Counters

Number values representing an incrementing value over time, tracking both the latest increment and the overall counter value.

Here's a log line representing a counter value:

    [nimrod][123456789][counter][total_players][100]

The Nimrod metrics server also computes and provides the following statistical information:

* Mean and variance of time intervals between counter updates.
* Mean and variance of the counter increment.

## Timers

Number values representing the elapsed time between start and stop of a given event.

Here's a log line starting a time computation:

    [nimrod][123456788][timer][login][start]

And here's a log line stopping a previously started time computation:

    [nimrod][123456789][timer][login][stop]

Elapsed time will be computed over the provided timestamps above (in the example above, the final value will be 1).

The Nimrod metrics server also computes and provides the following statistical information:

* Mean and variance of the elapsed time.

## Time-series history and sampling

For each metric, Nimrod stores all processed values in time-series which you can browse through the Nimrod HTTP interface, or query through external tools 
for monitoring purposes or further statistical analysis.

By the way, if your application produces lots of metrics, the time-series could grow very large, so Nimrod provides a random sampling method to reduce the 
size of the time-series history.
The sampling algorithm is based on two main concepts:

* Sampling frequency: how many metric values are fully stored before sampling takes place.
* Sampling factor: how much is the full sample reduced. 

Nimrod provides an extra guarantee: at any point in time, the latest N metric values, where N is equal to the sampling frequency, will always be fully kept; 
this means you can always rely on the latest N metrics to be completely accurate, which is very useful for monitoring purposes, when you usually want the freshest
data to be more accurate than the older one, in order to accurately detect anomalies.

So to make an example, if you setup a sampling frequency of 10000, and a factor of 10, every time Nimrod collects 10000 metric values the *previous* 10000 
values will be sampled and reduced to 1000, while the latest ones will be fully kept and sampled at the next round.

# Usage

## Download/Build

You can download the latest, ready-to-use, Nimrod binary version as a standalone self-contained jar from [here](https://github.com/downloads/sbtourist/nimrod/nimrod-0.6-standalone.jar).

Otherwise, you can check it out and build from source yourself:
Nimrod is written in wonderful Clojure, and you can build it with the excellent [Leiningen](http://github.com/technomancy/leiningen).
Once you have Leiningen installed, it is as easy as:

    $> lein deps && lein uberjar

## Configuration

Nimrod can be configured by creating and editing a *nimrod.conf* file placed in the same directory where you start the Nimrod process; it is based on the
[HOCON](https://github.com/typesafehub/config) format, kind of JSON but even easier for people to write.

You can pre-register log files to process, configure the metrics server and storage.

Logs can be pre-registered at startup by providing a *logs* block and a nested block for every log, identified by a unique name, as follows:

    logs {
        log_identifier_1 { 
            source : log_file_path
            interval : tailing_interval_in_millis
            end : true_for_tailing_from_end_false_otherwise
        }
        log_identifier_2 { 
            source : log_file_path
            interval : tailing_interval_in_millis
            end : true_for_tailing_from_end_false_otherwise
        }
        // ...
    }

Storage can be configured by providing the specific implementation, related options and sampling method, as follows:

    store {
        type : disk
        path : directory_for_the_nimrod_database
        options {
            "cache.results" : number_of_metric_values_cached_during_queries
            "defrag.limit" : percentage_of_wasted_space_after_which_defrag_is_executed
        }
        sampling {
            "metric.frequency" : sampling_frequency
            "metric.factor" : sampling_factor
        }
    }

Here is a more in-depth explanation: 

* type: currently, only the *disk* implementation is supported (the old *memory* implementation isn't supported anymore).
* path: the directory where the Nimrod database will be created/read. The database will be located in a directory named nimrod-data under the given path; if no path is provided, it will be created in the same directory where the Nimrod process is run.
* "cache.results" (quoting is mandatory): how many metric values are cached in memory by a single query to the time-series history; if the query exceeds that value, the results 
will be moved to disk and the query will be slower.
* "defrag.limit" (quoting is mandatory): percentage of wasted disk space after which defrag is executed.
* "metric.frequency" (quoting is mandatory): sampling frequency for metrics; you can define a sampling frequency for all metrics from a log file
by only specifying the log identifier (i.e. log1), or for all metrics of a given type from a log file by specifying the log identifier followed by a dot 
and the metric type (i.e. log1.gauge), or for a specific named metric of a given type from a log file by specifying the log identifier followed by a dot 
and the metric type followed by a dot and the metric name (i.e. log1.gauge.requests).
* "metric.factor" (quoting is mandatory): sampling factor for metrics, defined in the same way as metric.frequency.


Finally, the http server can be configured with the following options:

    server {
        port : http_server_port
        max-busy-requests : max_number_of_concurrent_requests_after_which_server_returns_503
    } 
    
More specifically:

* port: the port the http server listens to (mandatory).
* max-busy-requests: the max number of concurrent busy requests allowed by the server prior to returning "service unavailable" (503 HTTP status code); this is an advanced configuration option to be used to prevent flooding the server with long-running requests (optional).

## Startup

The Nimrod metrics server can be easily started as follows:

    $> java -cp nimrod-version-standalone.jar nimrod.core.startup

This will start the Nimrod http server and the log processing threads.

Please note Nimrod must be started on the same machine hosting the logs (either where they originated or where they have been moved to by tools like Syslog), in order to listen to and process them.

## Log files querying and management

You can query for registered logs as follows:

    GET /logs

And you can also stop listening/processing a log that was previously configured:

    POST /logs/log_id/stop

## Metrics querying and management

You can query all active metric types for a given log as follows:

    GET /logs/log_id

Once you have the metric type, you can query all actual metrics under that log and metric type:

    GET /logs/log_id/metric_type

Once you have a grasp of available Nimrod metrics by logs and types, specific Nimrod metrics can be queried as follows:

    GET /logs/log_id/metric_type/metric_id

Here, *metric_id* is the name of the specific metric you want to read.

You will always get the latest metric value, but you can also access its time-series history:

    GET /logs/log_id/metric_type/metric_id/history

Nimrod also provides a selection of rich APIs for querying and managing the time-series history.

You can browse through the history by age:

    GET /logs/log_id/metric_type/metric_id/history?age=max_allowed_age

Or time interval:

    GET /logs/log_id/metric_type/metric_id/history?from=start_time&to=end_time

Here, *max_allowed_age* represents the maximum age for the metrics to be returned in the history: it can be expressed in milliseconds time, or with a human-friendly time expression such as *1d*. Also, *start_time* and *end_time*, representing the time interval for history metrics, can be expressed similarly with either the unix time in milliseconds, or a human-friendly time expression such as *1d.ago*. Time expressions are composed by the number of time units (*1*) and the actual time unit (either *d* for days, *h* for hours, *m* for minutes, *s* for seconds), plus the *.ago* fixed string for the *from/to* interval parameters.

You can also use tags for filtering history results:

    GET /logs/log_id/metric_type/metric_id/history?tags=comma_separated_list_of_tags

Tags filtering can be used with both age and interval based queries.

History can be "pruned" by deleting values whose latest update happened before a given number of milliseconds:

    POST /logs/log_id/metric_type/metric_id/history/delete?age=max_allowed_age

Or by specifying a time interval:

    POST /logs/log_id/metric_type/metric_id/history/delete?from=start_time&to=end_time

History can also be aggregated, providing the following summary statistics: count, median and percentiles. 
Aggregation happens by again specifying the max age:

    GET /logs/log_id/metric_type/metric_id/history/aggregate?age=max_allowed_age

Or time interval:

    GET /logs/log_id/metric_type/metric_id/history/aggregate?from=start_time&to=end_time

Desired percentiles can also be specified as follows:

    GET /logs/log_id/metric_type/metric_id/history/aggregate?percentiles=comma_separated_list_of_percentages_ie_25,50,99

Same rules as before apply for time parameters in history removal and aggregation: they can be specified either in milliseconds or time expressions. 

Finally, metrics can be reset as follows:

    POST /logs/log_id/metric_type/metric_id/reset

Please note that this only resets the latest value, without affecting its history.

# Other Resources

* [API Reference](https://github.com/sbtourist/nimrod/wiki/API-Reference)
* [Frequently Asked Questions](https://github.com/sbtourist/nimrod/wiki/Frequently-Asked-Questions)

# Related Articles

* [Metrics visibility with Syslog-NG, Nimrod and Nagios](http://metabroadcast.com/blog/logfile-lovin-marrying-nimrod-and-nagios-for-software-visibility)

# Related Projects

* [Java Logging APIs](https://github.com/sbtourist/nimrod-java)
* [Node.js Logging APIs](https://github.com/Lukewh/nimrod-node)
* [Incanter-based Analytics](https://github.com/sbtourist/nimrod-incanter)
* [Nagios integration](https://github.com/mbst/nagios-nimrod)

# Feedback

For everything Nimrod-related, please join the nimrod-user group: http://groups.google.com/group/nimrod-user

# License

Copyright (C) 2011-2012 [Sergio Bossa](http://twitter.com/sbtourist)

Distributed under the [Apache Software License](http://www.apache.org/licenses/LICENSE-2.0.html).
