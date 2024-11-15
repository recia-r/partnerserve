(ns dojo.seed
  (:require
   [clojure.java.io :as io]
   [bloom.commons.uuid :as uuid]
   [dojo.config :refer [config]]
   [dojo.model :as model]
   [dojo.db :as db]))

(defn seed! []
  (let [topics (for [topic ["react" "clojure" "reagent" "re-frame" "javascript"]]
                 {:topic/id (uuid/random)
                  :topic/name topic})
        users [{:user/id (uuid/random)
                :user/name "Alice"
                :user/email "alice@example.com"
                :user/topic-ids {:topic-ids/session-type #{"match"}
                                 :topic-ids/skill-level #{"beginner"}}
                :user/availability (model/random-availability)
                :user/court-locations #{"flushing"}}
               {:user/id (uuid/random)
                :user/name "Bob"
                :user/email "bob@example.com"
                :user/topic-ids {:topic-ids/session-type #{"match"}
                                 :topic-ids/skill-level #{"beginner"}}
                :user/availability (model/random-availability)
                :user/court-locations #{"flushing"}}]]
    (doseq [topic topics]
      (db/save-topic! topic))
    (doseq [user users]
      (db/save-user! user))))