(ns dojo.client.state
  (:require
    [bloom.commons.ajax :as ajax]
    [reagent.core :as r]
    [re-frame.core :refer [reg-event-fx reg-fx reg-sub dispatch]]
    [dojo.model :as model]))

(defn key-by [f coll]
  (zipmap (map f coll)
          coll))

(defonce ajax-state (r/atom {}))

(reg-event-fx
  :add-user-selection!
  (fn [{db :db} [_ selection grouping]]
    {:db   (-> db
               (update-in [:db/user :user/topic-ids grouping] conj selection))
     :ajax {:method :put
            :uri    "/api/user/add-topic"
            :params {:topic-id selection
                     :grouping grouping}}}))

(reg-event-fx
  :add-user-court-selection!
  (fn [{db :db} [_ selection]]
    {:db   (-> db
               (update-in [:db/user :user/court-locations] conj selection))
     :ajax {:method :put
            :uri    "/api/user/add-court-location"
            :params {:court-location selection}}}))

(reg-event-fx
  :remove-user-court-selection!
  (fn [{db :db} [_ selection]]
    {:db   (-> db
               (update-in [:db/user :user/court-locations] disj selection))
     :ajax {:method :put
            :uri    "/api/user/remove-court-location"
            :params {:court-location selection}}}))


(reg-event-fx
  :remove-user-selection!
  (fn [{db :db} [_ selection grouping]]
    {:db   (-> db
               (update-in [:db/user :user/topic-ids grouping] disj selection))
     :ajax {:method :put
            :uri    "/api/user/remove-topic"
            :params {:topic-id selection
                     :grouping grouping}}}))

(reg-fx :ajax
  (fn [opts]
    (let [request-id (gensym "request")]
     (swap! ajax-state assoc request-id :request.state/in-progress)
     (ajax/request (assoc opts :on-success (fn [data]
                                            (swap! ajax-state dissoc request-id)
                                            (when (opts :on-success)
                                             ((opts :on-success) data))))))))

(reg-event-fx
  :initialize!
  (fn [_ _]
    {:db         {:db/checked-auth? false
                  :db/topics        {}
                  :db/court-location {:kissena "kissena" :flushing "flushing"}
                  :db/skill-level   {:beginner "beginner" :expert "expert"}
                  :db/session-type {:match "match" :rally "rally"}}
     :dispatch-n [[:fetch-user!]]}))

(reg-event-fx
  :fetch-user!
  (fn [_ _]
    {:ajax {:method :get
            :uri "/api/user"
            :on-success (fn [data]
                          (dispatch [::handle-user-data! data])
                          (dispatch [::mark-auth-completed!])
                          (dispatch [::fetch-other-data!]))
            :on-error (fn [_]
                       (dispatch [::mark-auth-completed!]))}}))

(reg-event-fx
  ::mark-auth-completed!
  (fn [{db :db} _]
    {:db (assoc db :db/checked-auth? true)}))

(reg-event-fx
  ::fetch-other-data!
  (fn [_ _]
    {:dispatch-n [[::fetch-topics!]
                  [::fetch-events!]]}))

(reg-event-fx
  ::fetch-topics!
  (fn [_ _]
    {:ajax {:method :get
            :uri "/api/topics"
            :on-success (fn [topics]
                          (dispatch [::store-topics! topics]))}}))

(reg-event-fx
  ::store-topics!
  (fn [{db :db} [_ topics]]
    {:db (update db :db/topics merge (key-by :topic/id topics))}))

(reg-event-fx
  ::fetch-events!
  (fn [_ _]
    {:ajax {:method :get
            :uri "/api/events"
            :on-success (fn [events]
                          (dispatch [::store-events! events]))}}))

(reg-event-fx
  ::store-events!
  (fn [{db :db} [_ events]]
    {:db (update db :db/events merge (key-by :event/id events))}))

(reg-event-fx
  ::maybe-set-time-zone!
  (fn [{db :db} _]
    (when (nil? (get-in db [:db/user :user/time-zone]))
     {:dispatch [:set-user-value! :user/time-zone (.. js/Intl DateTimeFormat resolvedOptions -timeZone)]})))

(reg-event-fx
  :new-topic!
  (fn [_ [_ topic-name]]
    {:ajax {:method :put
            :uri "/api/topics"
            :params {:name topic-name}
            :on-success (fn [topic]
                          (dispatch [::store-topics! [topic]])
                          (dispatch [:add-user-topic! (:topic/id topic)]))}}))

(reg-event-fx
  :log-in!
  (fn [_ [_ email]]
    {:ajax {:method :put
            :uri "/api/request-login-link-email"
            :params {:email email}
            :on-success (fn [data])}}))

(reg-event-fx
  :log-out!
  (fn [_ _]
    {:ajax {:method :delete
            :uri "/api/session"
            :on-success (fn []
                          (dispatch [::remove-user!]))}}))

(reg-event-fx
  ::remove-user!
  (fn [{db :db} _]
    {:db (dissoc db :db/user)}))

(reg-event-fx
  ::handle-user-data!
  (fn [{db :db} [_ data]]
    {:db (assoc db :db/user data)
     :dispatch [::maybe-set-time-zone!]}))

(reg-event-fx
  :set-availability!
  (fn [{db :db} [_ [day hour] value]]
    {:db (assoc-in db [:db/user :user/availability [day hour]] value)
     :ajax {:method :put
            :uri "/api/user/update-availability"
            :params {:day day
                     :hour hour
                     :value value}}}))

(reg-event-fx
  :opt-in-for-pairing!
  (fn [{db :db} [_ bool]]
    {:db (assoc-in db [:db/user :user/pair-next-week?] bool)
     :ajax {:method :put
            :uri "/api/user/opt-in-for-pairing"
            :params {:value bool}}}))

(reg-event-fx
  :set-user-value!
  (fn [{db :db} [_ k v]]
    {:db (assoc-in db [:db/user k] v)
     :ajax {:method :put
            :uri "/api/user/set-profile-value"
            :params {:k k
                     :v v}}}))

(reg-event-fx
  :update-subscription!
  (fn [{db :db} [_ status]]
    {:db (assoc-in db [:db/user :user/subscribed?] status)
     :ajax {:method :put
            :uri "/api/user/subscription"
            :params {:status status}}}))

(reg-event-fx
  :flag-event-guest!
  (fn [{db :db} [_ event-id value]]
    {:db (update-in db [:db/events event-id] (partial model/flag-other-user value) (get-in db [:db/user :user/id]))
     :ajax {:method :put
            :uri "/api/event/flag-guest"
            :params {:event-id event-id
                     :value value}}}))

(reg-sub
  :checked-auth?
  (fn [db _]
    (db :db/checked-auth?)))

(reg-sub
  :user
  (fn [db _]
    (db :db/user)))

(reg-sub
  :user-profile-value
  (fn [db [_ k]]
    (get-in db [:db/user k])))

(reg-sub
  :topics
  (fn [db _]
    (vals (db :db/topics))))

(reg-sub
  :skill-level
  (fn [db _]
    (vals (db :db/skill-level))))

(reg-sub
  :session-type
  (fn [db _]
    (vals (db :db/session-type))))

(reg-sub
  :court-location
  (fn [db _]
    (vals (db :db/court-location))))

(reg-sub
  :events
  (fn [db _]
    (vals (db :db/events))))

(reg-sub
  :db
  (fn [db _]
    db))
