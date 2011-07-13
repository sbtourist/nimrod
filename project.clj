(defproject nimrod "0.1-SNAPSHOT"
 :description "Not Invasive MetRics, Oh Dear!"
 :dependencies [
                [org.clojure/clojure "1.2.1"]
                [org.clojure/clojure-contrib "1.2.0"]
                [compojure "0.6.3"]
                [ring/ring-jetty-adapter "0.3.8"]
                [commons-io/commons-io "2.0.1"]
                [log4j/log4j "1.2.16" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                ]
 :dev-dependencies [[ring-mock "0.1.1"]]
 :aot [nimrod.web.startup]
 ;jvm-opts ["-agentlib:jdwp=transport=dt_socket,address=9000,server=y,suspend=n"]
 )
