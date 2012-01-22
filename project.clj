(defproject nimrod "0.5-SNAPSHOT"
 :description "Not Invasive MetRics, Oh Dear!"
 :dependencies [
                [org.clojure/clojure "1.3.0"]
                [org.clojure/java.jdbc "0.1.1"]
                [org.clojure/tools.logging "0.2.0"]
                [cheshire "2.0.4"]
                [compojure "1.0.0"]
                [ring/ring-core "1.0.1"]
                [ring/ring-jetty-adapter "1.0.1"]
                [com.typesafe.config/config "0.2.0"]
                [tayler/tayler "1.0"]
                [org.hsqldb/hsqldb "2.2.6"]
                [c3p0/c3p0 "0.9.1.2"]
                [com.google.guava/guava "r09"]
                [org.slf4j/slf4j-api "1.6.1"]
                [org.slf4j/slf4j-log4j12 "1.6.1"]
                ]
 :dev-dependencies [[ring-mock "0.1.1"]]
 :repositories {"typesafe" "http://repo.typesafe.com/typesafe/releases"}
 :java-source-path "src"
 :aot [nimrod.web.startup]
 )
