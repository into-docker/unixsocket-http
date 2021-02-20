(defproject unixsocket-http "1.0.7-SNAPSHOT"
  :description "A library to allow HTTP calls over a UNIX socket, e.g. for
                communicating with Docker."
  :url "https://github.com/into-docker/unixsocket-http"
  :license {:name "MIT License"
            :url "none"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.1.0"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.3.2"]
                 [com.squareup.okhttp3/okhttp "4.9.0"]
                 [into-docker/pem-reader "1.0.1"]
                 [com.squareup.okhttp3/okhttp-tls "4.9.0" :scope "provided"]
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.4.21-2"]]
  :exclusions [org.clojure/clojure]
  :aot [unixsocket-http.impl.FixedPathUnixSocket
        unixsocket-http.impl.FixedPathUnixSocketFactory
        unixsocket-http.impl.FixedPathTcpSocket
        unixsocket-http.impl.FixedPathTcpSocketFactory
        unixsocket-http.impl.SingletonSocketFactory
        unixsocket-http.impl.ResponseSocket
        unixsocket-http.impl.StreamingBody]
  :profiles {:dev
             {:dependencies [[com.squareup.okhttp3/mockwebserver "4.9.0"]
                             [org.clojure/test.check "1.1.0"]
                             [com.gfredericks/test.chuck "0.2.10"]]
              :global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.0.732"
                              :exclusions [org.clojure/spec.alpha]]
                             [lambdaisland/kaocha-cloverage "1.0.75"]
                             [org.clojure/java.classpath "1.0.0"]]}
             :ci
             [:kaocha
              {:global-vars {*warn-on-reflection* false}}]}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "ci"     ["with-profile" "+ci" "run" "-m" "kaocha.runner"
                      "--reporter" "documentation"
                      "--plugin"   "cloverage"
                      "--codecov"
                      "--no-cov-html"
                      "--cov-ns-exclude-regex" "unixsocket-http\\.impl\\..+"]}
  :pedantic? :abort)
