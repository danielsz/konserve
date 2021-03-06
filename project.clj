(defproject io.replikativ/konserve "0.4.9-SNAPSHOT"
  :description "Durable cross-platform key-value store protocol with core.async."
  :url "http://github.com/replikativ/konserve"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.9.0-alpha14" :scope "provided"]
                 [org.clojure/clojurescript "1.9.293" :scope "provided"]
                 [org.clojure/core.async "0.2.395"]

                 [org.clojure/data.fressian "0.2.1"] ;; for filestore
                 [io.replikativ/incognito "0.2.1"]

                 [io.replikativ/hasch "0.3.4"]]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]]
                   :figwheel {:nrepl-port 7888
                              :nrepl-middleware ["cider.nrepl/cider-middleware"
                                                 "cemerick.piggieback/wrap-cljs-repl"]}
                   :plugins [[lein-figwheel "0.5.8"]]}
             :test {:dependencies [[clj-time "0.13.0"]]}}

  :clean-targets ^{:protect false} ["target" "out" "resources/public/js"]

  :cljsbuild
  {:builds
   [{:id "cljs_repl"
     :source-paths ["src"]
     :figwheel true
     :compiler
     {:main konserve.indexeddb
      :asset-path "js/out"
      :output-to "resources/public/js/main.js"
      :output-dir "resources/public/js/out"
      :optimizations :none
      :pretty-print true}}]})
