(ns ^:no-doc unixsocket-http.impl.delegate
  (:import [java.lang.reflect Method Modifier]))

(defn is-public-instance-method?
  [^Method method]
  (let [modifiers (.getModifiers method)]
    (and (Modifier/isPublic modifiers)
         (not (Modifier/isStatic modifiers)))))

(defmacro delegate
  "Used inside a `:gen-class` namespace this macro will generate all
   method definitions of `class`, delegating calls to the value
   returned by `via`.

   You can set up exclusions using `except`."
  [{:keys [class via except]}]
  (let [include? (complement (set except))
        class (Class/forName (str class))]
    `(do
       ~@(for [^Method method (.getDeclaredMethods class)
               :when (is-public-instance-method? method)
               :let [method-name (.getName method)
                     method-sym (symbol method-name)
                     method-fn-sym (symbol (str "-" method-name))
                     args (->> (.getParameterTypes method)
                               (map
                                 (fn [^Class c]
                                   (with-meta
                                     (gensym (.getSimpleName c))
                                     {:type (symbol (.getName c))}))))]
               :when (include? method-sym)]
           `(defn ~method-fn-sym
              [~'this ~@args]
              (. (~via ~'this) ~method-sym ~@args))))))
