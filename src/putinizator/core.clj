(ns putinizator.core
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [net.cgrand.enlive-html :as html]
            [clojurewerkz.urly.core :as urly]))

(def putin-knows-that
  ["Владимир Путин был своевременно информирован, что "
   "Владимир Путин ясно дал понять, что "])

(defn uncapitalize
  "Converts first character of the string to upper-case, all other
  characters to lower-case."
  {:added "1.2"}
  [s]
  (when s
    (let [s (.toString s)]
      (if (< (count s) 2)
        (.toUpperCase s)
        (str (.toLowerCase (subs s 0 1))
             (subs s 1))))))

(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(defn get-content [node]
  (loop [node node]
    (let [content (-> node :content)]
      (if (and
           (not (empty? content))
           (string? (first content)))
        (last content)
        (when (not (empty? content))
          (recur (first content)))))))

(defn replace-url [node attr base-url url]
  (try
    (if (urly/relative? (-> node :attrs attr))
      (assoc node :attrs (assoc (:attrs node) attr (urly/resolve base-url url)))
      node)
    (catch Exception e node)))
   

(defn fix-url [base-url node]
  (condp (fn [x y] (contains? (:attrs y) x)) node
    :href (replace-url node :href base-url (-> node :attrs :href))
    :link (replace-url node :link base-url (-> node :attrs :link))
    :src (replace-url node :src base-url (-> node :attrs :src))
    node))

(defn fix-urls [node base-url]
  (let [fix-url (partial fix-url base-url)]
    (html/at
     node
     [:a] fix-url
     [:link] fix-url
     [:script] fix-url
     [:img] fix-url)))

(defn putinize-node [node]
  (let [content (get-content node)]
    (if content
      (assoc node :content (str (rand-nth putin-knows-that) (uncapitalize content)))
      node)))

(defn putinize [url]
  (let [url (urly/url-like url)
        abs-url (str (urly/protocol-of url) "://" (urly/host-of url))])
  (-> (html/at
      (fetch-url url)
      [:h1 :a] putinize-node
      ;; lenta.ru
      [:h2 :a] putinize-node
      [:h3 :a] putinize-node
      [:.b-tabloid__topic :a] putinize-node
      [:.b-yellow-box :a] putinize-node
      [:.b-top7-for-main :.item :a] putinize-node
      ;; tvrain.ru
      [:.after-player :.recent-news :a] putinize-node
      [:.custom-widget :.title] putinize-node
      [:.custom-widget :.item :a] putinize-node
      ;; lj navalny
      [:.entry-title :a] putinize-node
      [:.summary-list :ul :li :a] putinize-node)
     (fix-urls url)))

(defroutes app-routes
  (GET "/" {params :params}
       (html/emit* (putinize (:url params))))
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
