# Nimrod

Nimrod is a metrics server, inspired by the excellent [Coda Hale's Metrics library](https://github.com/codahale/metrics/), but purely based on log processing:
hence, it doesn't affect the way you write your applications, nor it has any side effect on them.
In other words, for those of you who love the bullet points:

* You don't have to import any libraries: just print some logs in a way that Nimrod is able to process, and run the Nimrod metrics server to process them.
* You can use Nimrod regardless of your programming language of choice.
* Nimrod bugs (yes, any piece of software will sooner or later have bugs) will never affect your application.

It currently provides the following features:

* Pre-defined and on-the-fly registration of log files.
* Almost real-time logs processing for metrics extraction.
* Different types of metrics with fundamental statistical information and time-series-like history.
* Web-based, Javascript-friendly JSON-over-HTTP server, with default support for Cross Origin Resource Sharing on GET requests.

# Metrics

## Logging

Nimrod metrics are printed on log files *by the user application*, while the Nimrod metrics server only listens to log and processes them: so,
metrics "production" is completely decoupled from metrics processing and storage.

Metrics must be printed in a Nimrod-specific format, providing the following information in square brackets:

* The **nimrod** fixed string.
* The metric **timestamp**, indicating *current unix time in milliseconds* and always *monotonic*.
* The metric **type**, among one of:
 * *alert*
 * *gauge*
 * *counter*
 * *timer*
* The metric **identifier** for the specified type.
* The metric **value**.
* An optional comma-separated list of metric **tags**, defining custom information.

Here's an example, without tags:

    [nimrod][123456789][counter][players][100]

But you can also interleave whatever you want between Nimrod-specific values, in order to make your logs more human-friendly:

    [nimrod][123456789][counter] - Current number of [players] is: [100]

And with tags:

    [nimrod][123456789][counter][players][100][game_code:123]

## Alerts

String values representing a generic message at a given time.

Here's a log line representing an alert value:

    [nimrod][123456789][alert][top_player][sergio]

The Nimrod metrics server also computes and provides the following statistical information:

* Average and variance of the elapsed time.

## Gauges

Number values representing a fixed indicator at a given time.

Here's a log line representing a gauge value:

    [nimrod][123456789][gauge][current_players][100]

The Nimrod metrics server also computes and provides the following statistical information:

* Average and variance of time intervals between measure updates.
* Average and variance of the measure.

## Counters

Number values representing an incrementing value over time, tracking both the latest increment and the overall counter value.

Here's a log line representing a counter value:

    [nimrod][123456789][counter][total_players][100]

The Nimrod metrics server also computes and provides the following statistical information:

* Average and variance of time intervals between counter updates.
* Average and variance of the counter increment.

## Timers

Number values representing the elapsed time between start and stop of a given event.

Here's a log line starting a time computation:

    [nimrod][123456788][timer][login][start]

And here's a log line stopping a previously started time computation:

    [nimrod][123456789][timer][login][stop]

Elapsed time will be computed over the provided timestamps above (in the example above, the final value will be 1).

The Nimrod metrics server also computes and provides the following statistical information:

* Average and variance of the elapsed time.

# Usage

## Download/Build

You can download the latest, ready-to-use, Nimrod binary version as a standalone self-contained jar from [here](https://github.com/downloads/sbtourist/nimrod/nimrod-0.3-standalone.jar).

Otherwise, you can check it out from sources and build by yourself:
Nimrod is written in wonderful Clojure, and you can build it with the excellent [Leiningen](http://github.com/technomancy/leiningen).
It is as easy as:

    $> lein deps && lein uberjar

## Configuration

Nimrod can be configured by editing a *nimrod.properties* file placed in the same directory you start the Nimrod process.

You can pre-register log files to process and define the metrics storage implementation.

Logs can be pre-registered at startup by providing the *nimrod.logs* property with a comma-separated list of log identifier, log path and related interval (in milliseconds) 
separated by colon, as follows:

    nimrod.logs = id1:log1:interval1,id2:log2:interval2, ...

Log identifiers must be unique, otherwise Nimrod will shutdown.

Metrics storage implementation can be configured to be either in-memory (volatile) or on-disk (persistent), 
by providing the *nimrod.store* property with a value of either _memory_ or _disk_:

    nimrod.store = memory|disk.

In case of persistent storage, metrics identifiers should be kept consistent between restarts: they should be configured to always refer to the same
"logical" file, that is, the file path could change but it should always refer to the same application and/or same set of metrics.

## Startup

First, you need to edit a nimrod.properties file in the same directory as your Nimrod jar; it can be just empty:

    $> touch nimrod.properties

Then, the Nimrod metrics server can be easily started as follows (replace "version" with the actual Nimrod version and "8000" with you port of choice):

    $> java -cp nimrod-version-standalone.jar nimrod.web.startup 8000

This will start the Nimrod web server and log processing activity.

Please note that Nimrod must be started on the same computer hosting the logs to listen to and process.

## Log files management

You can dynamically register the logs you want to listen to and process, by issuing the following request:

    POST /logs/log_id/start?file=log_file&interval=listen_interval

You have to provide the unique log identifier (*log_id*), the path of the log file (*log_file*), and the milliseconds interval among subsequent log reads (*listen_interval*).

You can query for registered logs too:

    GET /logs

And finally stop listening/processing logs:

    POST /logs/log_id/stop

Where *log_id* is the log identifier.

## Metrics management

Nimrod metric types for a given log, based on actual metrics, can be queried with the following request:

    GET /logs/log_id

Nimrod metrics for a given log and metric type can be queried with the following request:

    GET /logs/log_id/metric_type

Here, *log_id* is the log identifier and *metric_type* is the name of the metric type in plural form, that is either:
*alerts*, *gauges*, *counters* or *timers*.

Once you have a grasp of available Nimrod metrics by logs and types, a given, specific, Nimrod metric can be queried by issuing the following request:

    GET /logs/log_id/metric_type/metric_id

Where *metric_id* is the name of the specific metric you want to read.

You will always get the latest metric value, but you can also access the metric history as follows:

    GET /logs/log_id/metric_type/metric_id/history

And browse through the history by age and tags, providing the max age and/or comma separated list of tags to match:

    GET /logs/log_id/metric_type/metric_id/history?age=max_age&tags=tags_list

History is limited by default at 1000 values, but you can change it by specifying the *limit* query parameter.

History can be "pruned" by deleting values whose latest update happened before a given number of milliseconds:

    POST /logs/log_id/metric_type/metric_id/history/delete?age=milliseconds

Finally, uninteresting metrics can be completely deleted as follows:

    DELETE /logs/log_id/metric_type/metric_id

# Languages support

The _nimrod.java.utils.NimrodLogger_ class provides an easy way to print Nimrod logs from JVM languages.

First, you have to select the metric you want to log through one of the following static methods:

* NimrodLogger#forAlert(String name)
* NimrodLogger#forCounter(String name)
* NimrodLogger#forGauge(String name)
* NimrodLogger#forTimer(String name)

The _name_ argument should uniquely identify the metric among its type, and will be part of the full metric name composed as follows: _type.name_.
For example, the following method call (in Java) will produce a metric named _alert.failure_:

    NimrodLogger.forAlert("failure");

Then, you have to actually log the metric value through one of the following instance methods representing well-known log levels:

* NimrodLogger#debug(String value)
* NimrodLogger#debug(String value, String... tags)
* NimrodLogger#info(String value)
* NimrodLogger#info(String value, String... tags)
* NimrodLogger#warn(String value)
* NimrodLogger#warn(String value, String... tags)
* NimrodLogger#error(String value)
* NimrodLogger#error(String value, String... tags)

You can either log the metric value only, or an array of tags too.
For example, the following method call (in Java):

    NimrodLogger.forGauge("requests").info("100", "service:acme");

Will actually produce the following Nimrod log:

    [nimrod][123456789][gauge][requests][100][service:acme]

Please note that the timestamp value is automatically added based on the current time of logging.

Finally, NimrodLogger provides a few static helper methods:

* NimrodLogger#prefixWith(String prefix, String name): will prefix the metric name with the given prefix value.
* NimrodLogger#suffixWith(String name), String suffix: will suffix the metric name with the given suffix value.
* NimrodLogger#prefixWithThreadId(String name): will prefix the metric name with the hash code of the current thread.
* NimrodLogger#suffixWithThreadId(String name): will suffix the metric name with the hash code of the current thread.
* NimrodLogger#start(): will log the "start" metric value for timers.
* NimrodLogger#stop(): will log the "stop" metric value for timers.

NimrodLogger is based on [SLF4J](http://www.slf4j.org/), which provides bridges toward the most well known logging libraries.

# Feedback

For everything Nimrod-related, join the nimrod-user group: http://groups.google.com/group/nimrod-user

# License

Copyright (C) 2011 [Sergio Bossa](http://twitter.com/sbtourist)

Distributed under the [Apache Software License](http://www.apache.org/licenses/LICENSE-2.0.html).
