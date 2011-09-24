(defproject nimrod "0.2"
 :description "Not Invasive MetRics, Oh Dear!"
 :dependencies [
                [org.clojure/clojure "1.3.0"]
                [org.clojure/data.json "0.1.1"]
                [org.clojure/tools.logging "0.2.0"]
                [compojure "0.6.5"]
                [ring/ring-core "0.3.11"]
                [ring/ring-jetty-adapter "0.3.11"]
                [tayler/tayler "1.0"]
                [com.google.guava/guava "r09"]
                [org.slf4j/slf4j-api "1.6.1"]
                [org.slf4j/slf4j-log4j12 "1.6.1"]
                ]
 :dev-dependencies [[ring-mock "0.1.1"]]
 :java-source-path "src"
 :aot [nimrod.web.startup]
 )
