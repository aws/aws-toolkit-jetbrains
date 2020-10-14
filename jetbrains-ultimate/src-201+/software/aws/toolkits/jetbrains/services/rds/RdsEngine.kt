// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds

import icons.AwsIcons
import software.aws.toolkits.resources.message
import javax.swing.Icon

enum class RdsEngine(val engine: String, val displayName: String, val icon: Icon, val resourceType: String = engine) {
    MySql("mysql", message("rds.mysql"), AwsIcons.Resources.Rds.MYSQL) {
        override fun iamConnectionStringUrl(url: String) = "jdbc:$jdbcMysql://$url/"
    },
    Postgres("postgres", message("rds.postgres"), AwsIcons.Resources.Rds.POSTGRES) {
        override fun iamConnectionStringUrl(url: String) = "jdbc:$jdbcPostgres://$url/"
        override fun iamUsernameHook(username: String) = username.toLowerCase()
    },
    AuroraMySql("aurora", "${message("rds.aurora")}(${message("rds.mysql")})", AwsIcons.Resources.Rds.MYSQL, "aurora.mysql") {
        // The docs recommend using MariaDB instead of MySQL to connect to MySQL Aurora DBs:
        // https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Connecting.html#Aurora.Connecting.AuroraMySQL
        override fun iamConnectionStringUrl(url: String) = "jdbc:$jdbcMariadb://$url/"
    },
    AuroraPostgres("aurora-postgresql", "${message("rds.aurora")}(${message("rds.postgres")})", AwsIcons.Resources.Rds.POSTGRES, "aurora.postgres") {
        override fun iamConnectionStringUrl(url: String) = "jdbc:$jdbcPostgres://$url/"
        override fun iamUsernameHook(username: String) = username.toLowerCase()
    };

    abstract fun iamConnectionStringUrl(url: String): String

    /**
     * Some engines need to manipulate the username (e.g. Postgres should be lower-cased)
     */
    open fun iamUsernameHook(username: String) = username

    companion object {
        fun fromEngine(engine: String) = values().find { it.engine == engine } ?: throw IllegalArgumentException("Unknown RDS engine $engine")
    }
}
