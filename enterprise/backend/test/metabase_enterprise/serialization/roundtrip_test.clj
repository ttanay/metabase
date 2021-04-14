(ns metabase-enterprise.serialization.roundtrip-test
  (:refer-clojure :exclude [load])
  (:require [clojure.test :refer :all]
            [metabase-enterprise.serialization.cmd :as mb.ser.cmd]
            [metabase-enterprise.serialization.load-test :as load-test]
            [metabase.db.connection :as mdb.connection]
            [metabase.db.spec :as db.spec]
            [metabase.db.setup :as mdb.setup]
            [metabase.models.card :refer [Card]]
            [metabase.models.database :refer [Database]]
            [metabase.models.table :refer [Table]]
            [metabase.test :as mt]
            [metabase.test.data.users :as test-users]
            [metabase.util :as u]
            [toucan.db :as db]))

(deftest roundtrip-test
  (testing "Serialization roundtrip"
    (mt/test-drivers (mt/normal-drivers)
      (let [tmp-db-name        (mt/random-name)
            tmp-dir            "/tmp/dump-roundtrip-test"
            card-name          "Roundtrip Card 12"
            connection-details {:db (format "mem:%s;DB_CLOSE_DELAY=30" tmp-db-name)}
            target-jdbc-spec   (db.spec/h2 connection-details)]
        (mt/with-temp* [Database      [database]
                        Table         [table {:db_id (u/the-id database)}]
                        Card          [card  {:name          card-name
                                              :table_id      (u/the-id table)
                                              :database_id   (u/the-id database)
                                              :creator_id    (mt/user->id :crowberto)
                                              :dataset_query {:database (u/the-id database)
                                                              :type     :native,
                                                              :native   {:query "SELECT 1 AS \"val\""}}}]]
          (u/ignore-exceptions
            (#'load-test/delete-directory! tmp-dir))
          (mb.ser.cmd/dump tmp-dir (:email (test-users/fetch-user :crowberto)))
          (binding [mdb.connection/*db-type*   :h2
                    mdb.connection/*jdbc-spec* target-jdbc-spec
                    db/*quoting-style*         (mdb.connection/quoting-style :h2)
                    db/*db-connection*         target-jdbc-spec]
            (mdb.setup/migrate! target-jdbc-spec :up)
            (mb.ser.cmd/load tmp-dir {:on-error :abort :mode :update})
            ;; TODO: fix the assertion (i.e. don't compare IDs)
            (is (= card
                   (db/select-one Card :name card-name)))))))))





