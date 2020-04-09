(defproject socket-http "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "MIT License"
            :url "none"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.3.2"]
                 [com.squareup.okhttp3/okhttp "4.4.0"]]
  :aot [socket-http.impl.FixedPathUnixSocket
        socket-http.impl.FixedPathUnixSocketFactory]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}}}
  :pedantic? :abort)
