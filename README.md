# Nimrod

Nimrod is a simple metrics server, inspired by the excellent [Coda Hale's Metrics library](https://github.com/codahale/metrics/), but purely based on log processing:
hence, it doesn't affect the way you write your applications, nor it has any side effect on them.
In other words, for those of you who love the bullet points:

* You don't have to import any libraries: just print some logs in a way that Nimrod is able to process.
* You can use Nimrod regardless of your programming language of choice.
* Nimrod bugs (yes, any piece of software will sooner or later have bugs) will never affect your application.

It currently provides the following features:

* On-the-fly registration of logs to process.
* Almost real-time logs processing for metrics extraction.
* Different types of metrics with fundamental statistical information and time-based history management.
* Web-based, Javascript-friendly JSON-over-HTTP server, with default support for Cross Origin Resource Sharing on GET requests.

# Usage

## Download or Build

You can download the latest, ready-to-use, Nimrod binary version as a standalone self-contained jar from [here](https://github.com/downloads/sbtourist/nimrod/nimrod-0.2-standalone.jar).

Otherwise, you can check it out from sources and build by yourself:
Nimrod is written in wonderful Clojure, and you can build it with the excellent [Leiningen](http://github.com/technomancy/leiningen).
It is as easy as:

    $> lein uberjar

## Startup

Once you have built the Nimrod jar, you can easily start it as follows (replace 8000 with you port of choice):

    $> java -cp nimrod-version-standalone.jar nimrod.web.startup 8000

This will start the Nimrod server and log processing.

Logs can be pre-registered at startup by configuring a *nimrod.properties* file placed in the same directory you start Nimrod,
which must contain the *nimrod.logs* property with a comma-separated list of log paths with related intervals (in milliseconds) separated by colon, as follows:

    nimrod.logs = log1:interval1,log2:interval2, ...

The *nimrod.properties* file also provides the following configuration settings:

* nimrod.history.age : The default history age for newly created metrics, in milliseconds (defaults to 1 day).

Please note that Nimrod must be started on the same computer hosting the logs to listen to and process.

## Log

You can dynamically register the logs you want to listen to and process, by issuing the following request:

    POST /logs?file=log_file&interval=listen_interval

You have to provide the path of the log file (*log_file*), and the milliseconds interval among subsequent log reads (*listen_interval*):
Nimrod will return a JSON object with the log numeric identifier, which you will use later to query for metrics.

You can query for registered logs too:

    GET /logs

Then, start logging your metrics in the Nimrod-specific format, providing the following information in square brackets:

* The **nimrod** fixed string.
* The metric **timestamp** (generally in milliseconds, but can really be your preferred measure of time, provided it is always incrementing).
* The metric **type**, among one of:
 * *alert*
 * *gauge*
 * *counter*
 * *timer*
* The metric **identifier** for the specified type.
* The metric **value**.
* An optional comma-separated list of metric **tags**.

Here's an example, without tags:

    [nimrod][123456789][counter][players][100]

But you can also interleave whatever you want between Nimrod-specific values, in order to make your logs more human-friendly:

    [nimrod][123456789][counter] - Current number of [players] is: [100]

And with tags:

    [nimrod][123456789][counter][players][100][game_code:123]

## Query

Nimrod metrics for a given log and metric type can be queried with the following request:

    GET /logs/log_id/metric_type

Where *log_id* is the log identifier as provided by Nimrod after log registration and *metric_type* is the name of the metric type in plural form, that is either:
*alerts*, *gauges*, *counters* or *timers*.

Optionally, you can also pass a list of tags to narrow down and return only those metrics (for the given type) "marked" with a given set of tags:

    GET /logs/log_id/metric_type?tags=tags_list

Where *tags_list* is a comma-separated string of tag names.

Single Nimrod metrics can be queried by issuing the following request:

    GET /logs/log_id/metric_type/metric_id

Where *metric_id* is the name of the metric you want to read.

You will always get the latest metric value, but you can also access the metric history as follows:

    GET /logs/log_id/metric_type/metric_id/history

And browse through the history by tags, providing again the comma separated list of tags to match:

    GET /logs/log_id/metric_type/metric_id/history?tags=tags_list

Each metric history has a time-based expiration policy: metric values whose insertion date is older than a given age time will be removed from the history.
The history age can be customized as follows:

    POST /logs/log_id/metric_type/metric_id/history?age=history_age

Where *history_age* is the new maximum age for metric values, in milliseconds.

## Clean-up

Unused metrics can be cleaned up one by one by specifying their metric id:

     DELETE /logs/log_id/metric_type/metric_id

You can delete all metrics of a given type matching a given subset of tags:

    DELETE /logs/log_id/metric_type?tags=tags_list

Or, you can delete all metrics whose latest update happened before a given number of milliseconds:

    DELETE /logs/log_id/metric_type?age=milliseconds


# Metrics

## Alerts

String values representing a generic message at a given time.
It also provides the following statistical information:

* Average and variance of time intervals between alert updates.

Here's a log line representing an alert value:

    [nimrod][123456789][alert][top_player][sergio]

## Gauges

Number values representing a fixed indicator at a given time.
It also provides the following statistical information:

* Average and variance of time intervals between measure updates.
* Average and variance of the measure.

Here's a log line representing a gauge value:

    [nimrod][123456789][gauge][current_players][100]

## Counters

Number values representing an incrementing value over time, tracking both the latest increment and the overall counter value.
It also provides the following statistical information:

* Average and variance of time intervals between counter updates.
* Average and variance of the counter increment.

Here's a log line representing a counter value:

    [nimrod][123456789][counter][total_players][100]

## Timers

Number values representing the elapsed time between start and stop of a given event.
It also provides the following statistical information:

* Average and variance of the elapsed time.

Here's a log line starting a time computation:

    [nimrod][123456788][timer][login][start]

And here's a log line stopping a previously started time computation:

    [nimrod][123456789][timer][login][stop]

Elapsed time will be computed over the provided timestamps above (in the example above, the final value will be 1).

# Loggers

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
