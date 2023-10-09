(ns compat.main
  (:gen-class)
  (:require [unixsocket-http.core :as uhttp]
            [clojure.java.io :as io])
  (:import [org.newsclub.lib.junixsocket.common NarMetadata]
           [org.newsclub.net.unix AFUNIXSocket]
           [java.io File]
           [java.util Properties]))

(defn- junixsocket-version
  []
  (let [path "/META-INF/maven/com.kohlschutter.junixsocket/junixsocket-common/pom.properties"]
    (with-open [in (.getResourceAsStream AFUNIXSocket path)]
      (-> (doto (Properties.)
            (.load in))
          (.getProperty "version")))))

(defn- arch-and-os
  []
  (let [arch    (System/getProperty "os.arch")
        os      (-> (System/getProperty "os.name") (.replaceAll " " ""))]
    ;; Look, this is a fix for Github Actions' MacOS runner. I know this is not
    ;; pretty.
    (case [arch os]
      ["amd64" "MacOSX"] ["x86_64" "MacOSX"]
      [arch os])))

(defn run-selftest
  "Attempt to load the native library."
  []
  (let [library   (System/mapLibraryName
                    (str "junixsocket-native-" (junixsocket-version)))
        [arch os] (arch-and-os)
        path      (format "/lib/%s-%s-clang/jni/%s" arch os library)
        tmp-dir   (System/getProperty "java.io.tmpdir")]
    (println "Native Library: " library)
    (println "  Architecture: " arch)
    (println "  OS:           " os)
    (println "  Path:         " path)
    (println "  Temp Dir:     " tmp-dir)
    (println "Loading native library ...")
    (let [tmp-file (File/createTempFile "libtmp" library)
          tmp-path (.getAbsolutePath tmp-file)
          stream   (.getResourceAsStream NarMetadata path)]
      (or (when stream
            (println "  Copying to" tmp-path "...")
            (with-open [stream stream
                        out    (io/output-stream tmp-file)]
              (io/copy stream out))
            (println "  Loading" tmp-path "...")
            (System/load (.getAbsolutePath tmp-file))
            (println "  OK!")
            :ok)
          (do
            (println "The native library could not be found.")
            :fail)))))

(defn -main
  [& [socket-addr path]]
  (if (and socket-addr path)
    (let [client (uhttp/client socket-addr)]
      (prn (uhttp/get client path)))
    (when (= :fail (run-selftest))
      (System/exit 1))))
