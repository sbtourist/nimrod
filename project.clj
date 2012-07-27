(defproject nimrod "0.5.1-SNAPSHOT"
 :description "Not Invasive MetRics, Oh Dear!"
 :dependencies [
                [org.clojure/clojure "1.4.0"]
                [org.clojure/java.jdbc "0.2.0"]
                [org.clojure/tools.logging "0.2.0"]
                [cheshire "4.0.0"]
                [compojure "1.1.1"]
                [ring/ring-core "1.1.0"]
                [ring/ring-jetty-adapter "1.1.0"]
                [com.typesafe.config/config "0.2.0"]
                [tayler/tayler "1.1"]
                [org.hsqldb/hsqldb "2.2.9-SNAPSHOT"]
                [org.slf4j/slf4j-api "1.6.1"]
                [org.slf4j/slf4j-log4j12 "1.6.1"]]
 :repositories {"typesafe" "http://repo.typesafe.com/typesafe/releases"}
 :aot [nimrod.core.startup])
