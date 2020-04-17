(defproject unixsocket-http "1.0.4-SNAPSHOT"
  :description "A library to allow HTTP calls over a UNIX socket, e.g. for
                communicating with Docker."
  :url "https://github.com/into-docker/unixsocket-http"
  :license {:name "MIT License"
            :url "none"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.0.0"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.3.2"]
                 [com.squareup.okhttp3/okhttp "4.4.0"]
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.3.71"]]
  :exclusions [org.clojure/clojure]
  :aot [unixsocket-http.impl.FixedPathUnixSocket
        unixsocket-http.impl.FixedPathUnixSocketFactory
        unixsocket-http.impl.FixedPathTcpSocket
        unixsocket-http.impl.FixedPathTcpSocketFactory
        unixsocket-http.impl.StreamingBody]
  :profiles {:dev {:dependencies [[org.nanohttpd/nanohttpd "2.3.1"]
                                  [org.clojure/test.check "1.0.0"]
                                  [com.gfredericks/test.chuck "0.2.10"]]
                   :global-vars {*warn-on-reflection* true}}}
  :pedantic? :abort)
