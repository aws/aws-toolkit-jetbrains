// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds

import icons.AwsIcons
import software.aws.toolkits.resources.message
import javax.swing.Icon

// TODO: This can probably ultimately become [DbEngine] and encompass Redshift functionality too
sealed class RdsEngine(val engine: String, val displayName: String, val icon: Icon) {

    object MySql : MySqlBase("mysql", message("rds.mysql")) {
        override fun connectionStringUrl(endpoint: String) = "jdbc:$jdbcMysql://$endpoint/"
    }

    object AuroraMySql : MySqlBase("aurora", "${message("rds.aurora")}(${message("rds.mysql")})") {
        // The docs recommend using MariaDB instead of MySQL to connect to MySQL Aurora DBs:
        // https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Connecting.html#Aurora.Connecting.AuroraMySQL
        override fun connectionStringUrl(endpoint: String) = "jdbc:$jdbcMariadb://$endpoint/"
    }

    object Postgres: PostgresBase("postgres", message("rds.postgres"))

    object AuroraPostgres: PostgresBase("aurora-postgresql", "${message("rds.aurora")}(${message("rds.postgres")})")

    /**
     * Create a connection string, using the passed hostname/port [endpoint] parameter
     */
    abstract fun connectionStringUrl(endpoint: String): String

    /**
     * Some engines need to manipulate the [username] (e.g. Postgres should be lower-cased), this hook allows that
     */
    open fun iamUsername(username: String) = username

    abstract class PostgresBase(engine: String, displayName: String) : RdsEngine(engine, displayName, AwsIcons.Resources.Rds.POSTGRES) {
        override fun connectionStringUrl(endpoint: String) = "jdbc:$jdbcPostgres://$endpoint/"

        /**
         * In postgres this is case sensitive as lower case. If you add a db user for
         * IAM role "Admin", it is inserted to the db as "admin"
         */
        override fun iamUsername(username: String) = username.toLowerCase()
    }

    abstract class MySqlBase(engine: String, displayName: String) : RdsEngine(engine, displayName, AwsIcons.Resources.Rds.MYSQL)

    companion object {
        val values : Set<RdsEngine> = setOf(MySql, AuroraMySql, Postgres, AuroraPostgres)
        fun fromEngine(engine: String) = values.find { it.engine == engine } ?: throw IllegalArgumentException("Unknown RDS engine $engine")
    }
}
