(defproject onyx-dashboard "0.1.0-SNAPSHOT"
  :version-spec "0.1.~{:env/circle_build_num}"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["spec/clj"]

  :main onyx-dashboard.system

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2760" :scope "provided"]
                 [prismatic/schema "0.3.7"]
                 [com.stuartsierra/component "0.2.2"]
                 [com.taoensso/sente "1.3.0"]
                 ;[org.omcljs/om "0.8.8"]
                 [om "0.7.3"]
                 [com.taoensso/timbre "3.3.1"]
                 [cljs-uuid "0.0.4"]
                 [ring "1.3.2"]
                 [com.mdrogalis/onyx "0.5.2-20150207.184249-1"]
                 [com.cognitect/transit-clj "0.8.259"]
                 [com.cognitect/transit-cljs "0.8.205"]
                 [cljsjs/moment "2.9.0-0"]
                 [ring/ring-defaults "0.1.3"]
                 [compojure "1.3.1"]
                 [enlive "1.1.5"]
                 ;[org.om/om "0.8.1"]
                 [ankha "0.1.5.1-479897" :exclude [om]]
                 [fipp "0.5.1"]
                 [environ "1.0.0"]
                 [http-kit "2.1.19"]
                 ; make this explicit to fix uberjar?
                 [potemkin "0.3.11"]
                 [racehub/om-bootstrap "0.3.6" :exclusions [om]]
                 [prismatic/om-tools "0.3.10" :exclusions [om]]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-version-spec "0.0.4"]
            [lein-environ "1.0.0"]]

  :min-lein-version "2.5.0"

  :uberjar-name "onyx-dashboard.jar"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    "resources/public/js/out.js.map"
                                        ;:main onyx-dashboard.core.main
                                        :preamble      ["react/react.min.js"]
                                        :externs       ["react/externs/react.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :profiles {:dev {:source-paths ["env/dev/clj"]

                   :dependencies [[figwheel "0.2.2-SNAPSHOT"]
                                  [com.cemerick/piggieback "0.1.5"]
                                  [weasel "0.5.0"]
                                  [leiningen "2.5.1"]]

                   :repl-options {:init-ns onyx-dashboard.system
                                  :timeout 90000
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.2.2-SNAPSHOT"]]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]}

                   :env {:is-dev true}

                   :cljsbuild {:builds
                               {:app
                                {:source-paths ["env/dev/cljs"]}}}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
