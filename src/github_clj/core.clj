(ns github-clj.core
  (:require
    [tentacles.pulls :as pulls]
    [tentacles.issues :as issues]
    [clojure.edn :as edn])
  (:use [tentacles.core :only [api-call]]))

;; Requires a config.edn file in the cwd
;; sample contents:
;;{
;;  :auth "auth-token-from-GitHub:x-oauth-basic"
;;  :organization "some-org-or-userid"
;;  :repository "some-repo"
;;}
(def config (edn/read-string (slurp "config.edn")))

(def my-auth {:auth (:auth config)})
(def organization (:organization config))
(def repository (:repository config))

(defn call-for [fn & args]
  (apply fn organization repository args))

(defn pulls [base]
  (call-for pulls/pulls (merge my-auth {:base base})))

(defn get-prs [base]
  (remove nil?
    (map :number (pulls base))))

(defn pull [id]
  (call-for pulls/specific-pull id my-auth))

(defn mergeable-prs [base]
  (map :number
    (filter :mergeable
      (map pull (get-prs base)))))

(defn not-mergeable-prs [base]
  (map :number
    (remove :mergeable
      (map pull (get-prs base)))))

(defn issues [base]
  (call-for issues/issues (merge my-auth {:base base})))

(defn commits
  [id]
  (call-for pulls/commits id my-auth))

(defn last-commit
  [id]
  (last (remove #(= {} %) (commits id))))

(defn commit-statuses
  [sha]
  (remove nil?
    (map :state (api-call :get "/repos/%s/%s/statuses/%s" [organization repository sha] my-auth))))

(defn has-status? [status id]
  (=
    status
    (first (commit-statuses (:sha (last-commit id))))))

(defn has-comment? [id comment]
  (not
    (empty?
      (filter
        #(< -1 (.indexOf (str (:body %)) comment))
        (call-for issues/issue-comments id my-auth)))))

(defn comments
  [id comment-str]
  (map :id
    (filter #(< -1 (.indexOf (str (:body %)) comment-str))
      (call-for issues/issue-comments id my-auth))))

(defn delete-comment [id]
  (call-for issues/delete-comment id my-auth))

(defn create-comment [issue comment]
  (call-for issues/create-comment issue comment my-auth))

(defn failed-prs [base]
  (filter #(has-status? "failure" %) (get-prs base)))

(defn passed-prs [base]
  (filter #(has-status? "success" %) (mergeable-prs base)))

(defn retry-failed-prs [base]
  (map
    #(create-comment % "test this please.")
    (failed-prs base)))

(defn delete-comments [base comment]
  (map delete-comment
    (flatten
      (map
        #(comments % comment)
        (filter
          #(has-comment? % comment)
          (get-prs base))))))

(defn clean-pr-comments [base]
  (flatten
    (merge
      (delete-comments base "cake is a lie")
      (delete-comments base "just keep on trying")
      (delete-comments base "test this please"))))

(defn make-links [pr-numbers & {:keys [except] :or {except #{}}}]
  (apply prn (map #(prn (format "https://github.com/%s/%s/pull/%s" organization repository %)) (remove except pr-numbers))))

(defn create-pr [from base head body]
  (call-for pulls/create-pull from base head (merge {:body body } my-auth)))

(defn close-pr [id]
  (call-for pulls/edit-pull id (merge {:state "closed"} my-auth)))

(defn labels [id]
  (call-for issues/issue-labels id my-auth))

(defn add-labels [labels id]
  (call-for issues/add-labels id labels my-auth))

(defn move-pr [new-base id]
  ; GitHub PR returns the follow fields we need:
  ;:title => from
  ;:ref :head => head
  ;:body => body
  ;:login :user => username
  ;:ref :base => orig-base
  (let [{
    from :title
    body :body
    {head :ref} :head
    {orig-base :ref} :base
    {username :login} :user} (pull id)
    labels (remove nil? (map :name (labels id)))]
    (do
      (close-pr id)
      (add-labels labels (:number (create-pr from new-base head
        (format "%s\n\nPing: @%s\nMoved PR #%s from ``%s``" body username id orig-base)))))))
