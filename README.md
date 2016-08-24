# konserve

*Simple durability, made easy.*

A simple document store protocol defined with [core.async](https://github.com/clojure/core.async) semantics to allow Clojuresque collection operations on associative key-value stores, both from Clojure and ClojureScript for different backends. Data is generally serialized with [edn](https://github.com/edn-format/edn) semantics or, if supported, as native binary blobs and can be accessed similar to `clojure.core` functions `get-in`,`assoc-in` and `update-in`. `update-in` especially allows to run functions atomically and returns old and new value. Each operation is run atomically and must be consistent (in fact ACID), but further consistency is not supported (Riak, CouchDB and many scalable solutions don't have transactions over keys for that reason). This is meant to be a building block for more sophisticated storage solutions (Datomic also builds on kv-stores). An append-log for fast writes is also implemented.

This was initially implemented as an elementary storage protocol for [replikativ](https://github.com/replikativ/replikativ).

## Supported Backends

A file-system store in Clojure and [IndexedDB](https://developer.mozilla.org/en-US/docs/IndexedDB) for ClojureScript are provided as elementary reference implementations for the two most important platforms. No setup and no additional dependencies are needed.

### fs-store
The file-system store currently uses [fressian](https://github.com/clojure/data.fressian) and is quite efficient. It also allows to access values as a normal file-system file, e.g. to open it with a native database like HDF5 in Java.


### IndexedDB
For IndexedDB there is no internal JSON-representation of the underlying store like [transit](https://github.com/cognitect/transit-clj) yet, hence it is fairly slow for edn still. There is a JSON store protocol implemented for IndexedDB in case interoperability with a JavaScript application is wanted. Be careful not to confuse values with edn values, they are stored in separate locations and cannot clash.

### External Backends

The protocol is implemented for CouchDB in a separate project [konserve-couch](https://github.com/whilo/konserve-couch)

New storage backends, e.g. Riak, MongoDB, Redis, JDBC, WebSQL, Local-Storage are welcome.

## Benchmarks

Due to its simplicity it is also fairly fast as it directly serializes Clojure, e.g. with fressian, to durable storage. The file-store is CPU bound atm. More detailed benchmarks are welcome :).

~~~clojure
  (let [numbers (doall (range 1024))]
    (time
     (doseq [i (range 1000)]
       (<!! (assoc-in store [i] numbers)))))
  ;; fs-store: ~7.2 secs on my old laptop
  ;; mem-store: ~0.186 secs

  (let [numbers (doall (range (* 1024 1024)))]
    (time
     (doseq [i (range 10)]
       (<!! (assoc-in store [i] numbers)))))
  ;; fs-store: ~46 secs, large files: 1 million ints each
  ;; mem-store: ~0.003 secs
~~~

It is not necessarily fast depending on the usage pattern. The general idea is to write most values once (e.g. in form of index fragments) and only update one place once all data is written, similar to Clojure's persistent datastructures. To store values under non-conflicting keys, have a look at [hasch](https://github.com/replikativ/hasch). 



## Serialization formats

Different formats for `edn` serialization like [fressian](https://github.com/clojure/data.fressian), [transit](http://blog.cognitect.com/blog/2014/7/22/transit) or a simple `pr-str` version are supported and can be combined with different stores. Stores have reasonable default setting. You can also extend the serialization protocol to other formats if you need it. You also need to provide [incognito](https://github.com/replikativ/incognito) support for records, if you need them.

### Tagged Literals

You can read and write custom records according to [incognito](https://github.com/replikativ/incognito).


## Usage

Add to your leiningen dependencies:
[![Clojars Project](http://clojars.org/io.replikativ/konserve/latest-version.svg)](http://clojars.org/io.replikativ/konserve)

For simple purposes a memory store wrapping an Atom is implemented as well:
~~~clojure
(ns test-db
  (:require [konserve.memory :refer [new-mem-store]]
            [konserve.core :as k]))

(go (def my-db (<! (new-mem-store)))) ;; or
(go (def my-db (<! (new-mem-store (atom {:foo 42})))))
~~~

From a Clojure REPL run:
~~~clojure
(ns test-db
    (:require [konserve.filestore :refer [new-fs-store]]
              [konserve.core :as k]
              [clojure.core.async :as async :refer [<!!]]))

(def store (<!! (new-fs-store "/tmp/store")))

(<!! (k/assoc-in store ["foo" :bar] {:foo "baz"}))
(<!! (k/get-in store ["foo"]))
(<!! (k/exists? store "foo"))

(<!! (k/assoc-in store [:bar] 42))
(<!! (k/update-in store [:bar] inc))
(<!! (k/get-in store [:bar]))

(<!! (k/append store :error-log {:type :horrible}))
(<!! (k/log store :error-log))

(let [ba (byte-array (* 10 1024 1024) (byte 42))]
  (time (<!! (k/bassoc store "banana" ba))))
(<!! (k/bget store "banana" :input-stream))
~~~


In ClojureScript from a browser (you need IndexedDB available in your js env):
~~~clojure
(ns test-db
  (:require [konserve.indexeddb :refer [new-indexeddb-store]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(go (def my-db (<! (new-indexeddb-store "konserve"))))

(go (println "get:" (<! (k/get-in my-db ["test" :a]))))

(go (doseq [i (range 10)]
       (<! (k/assoc-in my-db [i] i))))

;; prints 0 to 9 each on a line
(go (doseq [i (range 10)]
      (println (<! (k/get-in my-db [i])))))

(go (println (<! (k/assoc-in my-db ["test"] {:a 1 :b 4.2}))))

(go (println (<! (k/update-in my-db ["test" :a] inc))))
;; => "test" contains {:a 2 :b 4.2}
~~~

An example for ClojureScript with IndexedDB is:
~~~clojure
(defrecord Test [a])

(go (def my-store (<! (new-indexeddb-store "konserve" (atom {'user.Test
                                                             map->Test})))))

(go (println (<! (k/assoc-in my-store ["rec-test"] (Test. 5)))))
(go (println (<! (k/get-in my-store ["rec-test"]))))
~~~

For more examples have a look at the comment blocks at the end of the respective namespaces.

## JavaScript bindings

There are experimental javascript bindings in the `konserve.js` namespace:

~~~javascript
goog.require("konserve.js");

konserve.js.new_mem_store(function(s) { store = s; });
# or
konserve.js.new_indexeddb_store("test_store", function(s) { store = s; })

konserve.js.exists(store, ["foo"], function(v) { console.log(v); });
konserve.js.assoc_in(store, ["foo"], 42, function(v) {});
konserve.js.get_in(store,
                   ["foo"],
                   function(v) { console.log(v); });
konserve.js.update_in(store,
                      ["foo"],
                      function(v) { return v+1; },
                      function(res) { console.log("Result:", res); });
~~~

## TODO
- add transit cljs support (once it is declared stable)
- implement generic cached store(s) to wrap durable ones
- more backends

## Changelog

### 0.4.0
- store the key in the filestore and allow to iterate stored keys (not binary atm.)
- implement append functions to have high throughput append-only logs
- use core.async based locking on top-level API for all stores
- allow to delete a file-store

### 0.3.6
- experimental JavaScript bindings

### 0.3.4
- use fixed incognito version

### 0.3.0 - 0.3.2
- fix return value of assoc-in

### 0.3.0-beta3
- Wrap protocols in proper Clojure functions in the core namespace.
- Implement assoc-in in terms of update-in
- Introduce serialiasation protocol with the help of incognito and decouple stores

### 0.3.0-beta1
- filestore: disable cache
- factor out all tagged literal functions to incognito
- use reader conditionals
- bump deps

### 0.2.3
- filestore: flush output streams, fsync on fs operations
- filestore can be considered beta quality
- couchdb: add -exists?
- couchdb: move to new project
- remove logging and return ex-info exceptions in go channel

### 0.2.2
- filestore: locking around java strings is a bad idea, use proper lock objects
- filestore: do io inside async/thread (like async's pipeline) to not block the async threadpool
- filestore: implement a naive cache (flushes once > 1000 values)
- filestore, indexeddb: allow to safely custom deserialize file-inputstream in transaction/lock
- filestore, indexeddb, memstore: implement -exists?

### 0.2.1
- filestore: fix fressian collection types for clojure, expose read-handlers/write-handlers
- filestore: fix -update-in behaviour for nested values
- filestore: fix rollback renaming order

### 0.2.0
- experimental native ACID file-store for Clojure
- native binary blob support for file-store, IndexedDB and mem-store

## License

Copyright © 2014-2016 Christian Weilbach & Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
