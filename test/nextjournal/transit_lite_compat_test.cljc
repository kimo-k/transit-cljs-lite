(ns nextjournal.transit-lite-compat-test
  "Cross-library compatibility: cognitect.transit JSON → transit-lite read.

  Fixture strings are captured from cognitect.transit/write so transit-lite
  must be able to decode them to the original Clojure values. The CLJ side
  re-verifies the fixtures via cognitect.transit; the CLJS side runs the
  actual compat assertions against transit-lite/read-str."
  (:require [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]
            #?(:cljs [nextjournal.transit-lite :as tx])
            #?(:clj [cognitect.transit :as ct]))
  #?(:clj (:import (java.io ByteArrayInputStream))))

(def fixtures
  "Each entry: encoded JSON from cognitect.transit (with :transform write-meta),
   plus the expected Clojure value. :expected-meta, when present, is checked
   against the metadata of the decoded value."
  [{:desc     "plain map"
    :expected {:a 1 :b 2}
    :encoded  "[\"^ \",\"~:a\",1,\"~:b\",2]"}

   {:desc     "vector of maps with repeated keys (cache refs ^0 ^1)"
    :expected [{:id 1 :name "a"} {:id 2 :name "b"} {:id 3 :name "c"}]
    :encoded  "[[\"^ \",\"~:id\",1,\"~:name\",\"a\"],[\"^ \",\"^0\",2,\"^1\",\"b\"],[\"^ \",\"^0\",3,\"^1\",\"c\"]]"}

   {:desc     "plain string value should not enter the read cache"
    :expected {:url "/some-path" :next :foo :foo :end}
    :encoded  "[\"^ \",\"~:url\",\"/some-path\",\"~:next\",\"~:foo\",\"^2\",\"~:end\"]"}

   {:desc     "deeply nested, exercises ^6 cache ref"
    :expected {:dispatch-url "/offworld-dispatch"
               :actions      [[:nextjournal.offworld.demo.ui.nested-grid/init-local
                               [:nextjournal.baseline/local :grid]]]
               :trigger      :lifecycle
               :lifecycle    :replicant/mount}
    :encoded  "[\"^ \",\"~:dispatch-url\",\"/offworld-dispatch\",\"~:actions\",[[\"~:nextjournal.offworld.demo.ui.nested-grid/init-local\",[\"~:nextjournal.baseline/local\",\"~:grid\"]]],\"~:trigger\",\"~:lifecycle\",\"^6\",\"~:replicant/mount\"]"}

   {:desc          "with-meta value encoded as [\"~#with-meta\", ...] array"
    :expected      [:a :b :c]
    :expected-meta {:source :test :n 42}
    :encoded       "[\"~#with-meta\",[[\"~:a\",\"~:b\",\"~:c\"],[\"^ \",\"~:source\",\"~:test\",\"~:n\",42]]]"}

   {:desc     "set tagged value as array"
    :expected #{:x :y :z}
    :encoded  "[\"~#set\",[\"~:y\",\"~:z\",\"~:x\"]]"}

   {:desc     "list tagged value as array"
    :expected (list :a :b :c)
    :encoded  "[\"~#list\",[\"~:a\",\"~:b\",\"~:c\"]]"}

   {:desc     "symbol and uuid values"
    :expected {:s 'foo/bar :u #uuid "00000000-0000-0000-0000-000000000001"}
    :encoded  "[\"^ \",\"~:s\",\"~$foo/bar\",\"~:u\",\"~u00000000-0000-0000-0000-000000000001\"]"}

   {:desc     "namespaced keyword keys"
    :expected {:ns/a 1 :ns/b 2 :other/a 3}
    :encoded  "[\"^ \",\"~:ns/a\",1,\"~:ns/b\",2,\"~:other/a\",3]"}

   {:desc     "uuid value must not occupy a cache slot (keyword cache refs after uuid must resolve correctly)"
    :expected {:id #uuid "00000000-0000-0000-0000-000000000001" :type :foo :other :foo}
    :encoded  "[\"^ \",\"~:id\",\"~u00000000-0000-0000-0000-000000000001\",\"~:type\",\"~:foo\",\"~:other\",\"^2\"]"}])

#?(:clj
   (defn- ct-read [s]
     (let [bis (ByteArrayInputStream. (.getBytes ^String s "utf-8"))]
       (ct/read (ct/reader bis :json)))))

(defn- read-fixture [encoded]
  #?(:clj  (ct-read encoded)
     :cljs (tx/read-str encoded)))

(deftest decode-cognitect-transit-fixtures
  (doseq [{:keys [desc expected expected-meta encoded]} fixtures]
    (testing desc
      (let [decoded (read-fixture encoded)]
        (is (= expected decoded))
        (when expected-meta
          (is (= expected-meta (meta decoded))))))))
