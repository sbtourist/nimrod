(defproject nimrod "0.5"
 :description "Not Invasive MetRics, Oh Dear!"
 :dependencies [
                [org.clojure/clojure "1.4.0"]
                [org.clojure/java.jdbc "0.2.0"]
                [org.clojure/tools.logging "0.2.0"]
                [cheshire "4.0.0"]
                [compojure "1.0.0"]
                [ring/ring-core "1.0.1"]
                [ring/ring-jetty-adapter "1.0.1"]
                [com.typesafe.config/config "0.2.0"]
                [tayler/tayler "1.1"]
                [org.hsqldb/hsqldb "2.2.8"]
                [c3p0/c3p0 "0.9.1.2"]
                [org.slf4j/slf4j-api "1.6.1"]
                [org.slf4j/slf4j-log4j12 "1.6.1"]]
 :dev-dependencies [[ring-mock "0.1.1"]]
 :repositories {"typesafe" "http://repo.typesafe.com/typesafe/releases"}
 :aot [nimrod.web.startup])
