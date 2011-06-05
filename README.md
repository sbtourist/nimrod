# Nimrod

Nimrod is a simple metrics server, inspired by the excellent [Coda Hale's Metrics library](https://github.com/codahale/metrics/), but purely based on log processing:
hence, it doesn't affect the way you write your applications, nor it has any side effect on them.
In other words, for those of you who love the bullet points:

* You don't have to import any libraries: just print some logs in a way that Nimrod is able to process.
* You can use Nimrod regardless of your programming language of choice.
* Nimrod bugs (yes, any piece of software will sooner or later have bugs) will never affect your application.

It currently provides the following features:

* On-the-fly registration of logs to process.
* Dynamic, almost real-time processing of logs.
* Different types of metrics with basic statistical information attached and history management.
* No configuration, completely web-based.
* Json-over-Http.

# Usage

## Get and Build

Nimrod is written in wonderful Clojure and doesn't currently come in binary form, so you have to checkout the git repository and build it with the excellent [Leiningen](http://github.com/technomancy/leiningen).
It is as easy as:

    $> lein uberjar

## Startup

Once you have built the Nimrod jar, you can easily start it as follows (replace 8000 with you port of choice):

    $> java -cp nimrod-0.1-SNAPSHOT-standalone.jar nimrod.web.startup 8000

This will start the Nimrod server and log processing.

Logs can be pre-registered at startup by providing a *nimrod.properties* file in the same directory you start Nimrod,
and containing the *nimrod.logs* property with a comma-separated list of log paths with related intervals, as follows:

    nimrod.logs = log1:interval1,log2:interval2, ...

Please note that Nimrod must be started on the same computer hosting the logs to listen to and process.

## Log

You can register the logs you want to listen to and process, by issuing the following request:

    POST /logs?file=:log_file&interval=:listen_interval

You have to provide the path of the log file, and the milliseconds interval among subsequent log reads: Nimrod will return a JSON object with the log numeric identifier,
which you will use later to query for metrics.

You can query for registered logs too:

    GET /logs

Then, start logging your metrics in the Nimrod-specific format, providing the following information in square brackets:

* The "nimrod" fixed string.
* The metric timestamp (in milliseconds).
* The metric type, among one of:
 * "gauges"
 * "measures"
 * "counters"
 * "timers"
* The metric identifier for the specified type.
* The metric value.
* An optional comma-separated list of metric tags.

Here's an example, without tags:

    [nimrod][123456789][gauges][player][sergio]

But you can also interleave whatever you want between Nimrod-specific values, in order to make your logs more human-friendly:

    [nimrod][123456789][gauges] - Current [player] is: [sergio]

And with tags:

    [nimrod][123456789][gauges][player][sergio][twitter:sbtourist,github:sbtourist]

## Query

Nimrod metrics can be queried by issuing the following request:

    GET /logs/:log_id/:metric_type/:metric_id

Where *:log_id* is the log identifier as provided by Nimrod after log registration, *:metric_type* is the name of the metric type as specified before and
*:metric_id* is the name of the metric you want to read.

You will always get the latest metric value, but you can also access the metric history as follows:

    GET /logs/:log_id/:metric_type/:metric_id/history

And browse through the history by tags, providing the comma separated list of tags to match:

    GET /logs/:log_id/:metric_type/:metric_id/history/:tags

Metric history and its depth can be reset as follows:

    POST /logs/:log_id/:metric_type/:metric_id/history?limit=:history_depth

# Metrics

## Gauges

String values representing a generic indicator at a given time.

## Measures

Number values representing a fixed measure at a given time.
It also provides the following statistical information:

* Average and variance of time intervals between measure updates.
* Average and variance of the measure.

## Counters

Number values representing an incrementing measure over time.
It also provides the following statistical information:

* Average and variance of time intervals between counter updates.
* Average and variance of the counter increment.

## Timers

Number values representing the elapsed time between start and end of a given event, cyclically computed at each invocation.
It also provides the following statistical information:

* Average and variance of the elapsed time.

## License

Copyright (C) 2011 Sergio Bossa

Distributed under the [Apache Software License](http://www.apache.org/licenses/LICENSE-2.0.html).
