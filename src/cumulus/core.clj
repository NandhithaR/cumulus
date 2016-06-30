(ns cumulus.core
  (:require
    [clojure.string :as str]
    [cumulus.internal :as i]))


(defn raw-params
  [driver-classname
   jdbc-url
   test-query]
  {:classname  driver-classname
   :jdbc-url   jdbc-url
   :test-query test-query})


(defn jdbc-params
  "Given database-specific parameters, return a map of fundamental JDBC params consisting of
  the following mandatory keys (all of them having string values):
  :driver-class (fully qualified class name)
  :jdbc-url
  :test-query"
  [db-type db-params]
  
  (when-let [key (get db-params :port)]
    (i/type-check db-params :port))
  
  (when-let [key (get db-params :database)]
   (i/type-check-string db-params :database))
  
  (when-let [key (get db-params :host)]
    (i/type-check-string db-params :host))
  
  (let [R (partial i/reqd db-params)
        P (fn ([k] (get db-params k))
            ([k d] (get db-params k d)))
        Q (fn ([k] (if-let [v (P k)] 
                     (str ":" v)
                     ""))
            ([k d] (str ":" (P k d))))]
    
    (case db-type
      ;;embedded
      :odbc            (raw-params
                         "sun.jdbc.odbc.JdbcOdbcDriver"
                         (format "jdbc:odbc:%s"  (R :dsn))
                         nil)
      
      :axiondb         (raw-params 
                         "org.axiondb.jdbc.AxionDriver"
                         (let [target (:target db-params)]
                           (case target
                             :memory (format "jdbc:axiondb:%s"  (R :database))
                             :filesys (format "jdbc:axiondb:%s:%s" (R :database) (R :db-path))))
                         "SELECT 1")
      
      :derby           (raw-params 
                         "org.apache.derby.jdbc.EmbeddedDriver"
                         (let [target (:target db-params)]
                           (case target
                             :memory   (format "jdbc:derby:memory:%s;create=true;"     (R :database))
                             :filesys   (format "jdbc:derby:directory:%s;create=true;" (R :database))
                             :classpath (format "jdbc:derby:classpath:%s"              (R :database))
                             :jar       (format "jdbc:derby:jar:(%s)%s"                (R :jar-path) (R :database))
                             :network   (format "jdbc:derby://%s%s/%s;create=true;"    (R :host) (Q :port) (R :database))
                             (i/expected ":target to be :memory, :filesys, :classpath, :jar or :network" target)))
                         "values(1)")
      
      :h2              (raw-params
                         "org.h2.Driver"
                         (let [target (:target db-params)]
                           (case target 
                             :memory  (format "jdbc:h2:mem:%s"             (R :database))
                             :filesys (format "jdbc:h2:file:%s"            (R :database))
                             :network (format "jdbc:h2:tcp:%s%s/%s"        (R :host) (Q :port) (R :database))
                             (i/expected ":target to be :memory, :filesys or :network" target)))
                         "SELECT 1")
      
      :hsqldb          (raw-params 
                         "org.hsqldb.jdbcDriver"
                         (let [target (:target db-params)]
                           (case target
                             :memory  (format "jdbc:hsqldb:mem:%s"         (R :database))
                             :filesys (format "jdbc:hsqldb:file:%s"        (R :database))
                             :network (format "jdbc:hsqldb:hsql://%s%s/%s" (R :host) (Q :port) (R :database))
                             (i/expected ":target to be :memory, :filesys or :network" target)))
                         "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS")
      
      :mckoi           (raw-params 
                         "com.mckoi.JDBCDriver"
                         (format "jdbc:mckoi:local://%s"                   (R :database))
                         "SELECT 1")
      
      :sqlite          (raw-params 
                         "org.sqlite.JDBC"
                         (let [target (:target db-params)]
                           (case target 
                             :memory  (format "jdbc:sqlite::memory:")
                             :filesys (format "jdbc:sqlite:%s"             (R :database))
                             (i/expected ":target to be :memory or :filesys" target)))
                         "SELECT 1")
      
      ;; network OSS
      :cubrid          (raw-params 
                         "cubrid.jdbc.driver.CUBRIDDriver"
                         (format "jdbc:cubrid:%s%s:%s"                 (R :host) (Q :port) (R :database))
                         "SELECT 1;")
      
      :firebird        (raw-params
                         "org.firebirdsql.jdbc.FBDriver"
                         (format "jdbc:firebirdsql://%s%s/%s"          (R :host) (Q :port) (R :database))
                         "SELECT CAST(1 AS INTEGER) FROM rdb$database;")
      
      :jtds-sqlserver  (raw-params
                         "net.sourceforge.jtds.jdbc.Driver"
                         (format "jdbc:jtds:sqlserver://%s%s%s"        (R :host) (Q :port) (R :database))
                         "select 1;")
      
      :jtds-sybase     (raw-params 
                         "net.sourceforge.jtds.jdbc.Driver"
                         (format "jdbc:jtds:sybase://%s%s%s"           (R :host) (Q :port) (R :database))
                         "select 1;")
      
      :monetdb         (raw-params
                         "nl.cwi.monetdb.jdbc.MonetDriver"
                         (format "jdbc:monetdb://%s%s/%s"              (R :host) (Q :port) (R :database))
                         "SELECT 1;")
      
      :mysql           (raw-params 
                         "com.mysql.jdbc.Driver"
                         (format "jdbc:mysql://%s%s/%s"                (R :host) (Q :port) (R :database))
                         "SELECT 1;")
      
      :postgresql      (raw-params 
                         "org.postgresql.Driver"
                         (format "jdbc:postgresql://%s%s/%s"           (R :host) (Q :port) (R :database))
                         "SELECT version();")
      
      ;; network proprietary
      :db2             (raw-params 
                         "com.ibm.db2.jcc.DB2Driver"
                         (format "jdbc:db2://%s%s/%s"                  (R :host) (Q :port) (R :database))
                         "select * from sysibm.SYSDUMMY1;")
      
      :oracle          (raw-params
                         "oracle.jdbc.driver.OracleDriver"
                         (let [style (:style db-params)]
                           (case style
                             :system-id    (format "jdbc:oracle:thin:@%s%s:%s"        (R :host) (Q :port) (R :database))
                             :service-name (format "jdbc:oracle:thin:@//%s%s/%s"      (R :host) (Q :port) (R :database))
                             :tns-name     (format "jdbc:oracle:thin:@%s"              (R :database))
                             :ldap         (format "jdbc:oracle:thin:@ldap://%s/%s:%s" (R :host) (Q :port) (R :database))
                             :oci          (format "jdbc:oracle:oci:@%s"               (R :database))
                             :oci8         (format "jdbc:oracle:oci8:@%s"              (R :database))
                             (i/expected ":target to be :system-id, :service-name, :tns-name, :ldap,oci or :oci8" style)))
                         "SELECT 1 FROM DUAL")
      
      :sapdb           (raw-params
                         "com.sap.dbtech.jdbc.DriverSapDB"
                         (format "jdbc:sapdb://%s%s/%s"                 (R :host) (Q :port) (R :database))
                         "SELECT 1 FROM DUAL")
      
      :sqlserver       (raw-params
                         "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                         (format "jdbc:sqlserver://%s%s%s"            (Q :host) (Q :instance) (Q :port))
                         "SELECT 1")
      
      :sybase          (raw-params
                         "com.sybase.jdbc2.jdbc.SybDriver"
                         (if (get db-params :database)
                               (format "jdbc:sybase:Tds:%s%s?ServiceName=%s?"  (R :host) (Q :port) (Q :database))
                               (format "jdbc:sybase:Tds:%s%s" (R :host) (Q :port)))
                             "SELECT 1")
      
      (throw (IllegalArgumentException.
               (format "Database/adapter type %s is not supported" db-type))))))
