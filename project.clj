(defproject unixsocket-http "0.1.0-SNAPSHOT"
  :description "A library to allow HTTP calls over a UNIX socket, e.g. for
                communicating with Docker."
  :url "https://github.com/into-docker/unixsocket-http"
  :license {:name "MIT License"
            :url "none"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.3.2"]
                 [com.squareup.okhttp3/okhttp "4.4.0"]]
  :aot [unixsocket-http.impl.FixedPathUnixSocket
        unixsocket-http.impl.FixedPathUnixSocketFactory
        unixsocket-http.impl.StreamingBody]
  :profiles {:dev {:dependencies [[org.nanohttpd/nanohttpd "2.3.1"]]
                   :global-vars {*warn-on-reflection* true}}}
  :pedantic? :abort)
