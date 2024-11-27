package org.jetbrains.exposed.sql.tests.ydb

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.YdbDialect
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.sql.Connection
import kotlin.test.assertEquals

object Users : Table() {
    val id: Column<String> = text("id")
    val name: Column<String> = text("name")
    val cityId: Column<Int?> = integer("city_id").nullable()

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID") // name is optional here
}

object Cities : Table() {
    val id: Column<Int> = integer("id").autoIncrement()
    val name: Column<String> = text("name")

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}

class YdbTest {
    @Test
    fun testLocate() {
        transaction {
            SchemaUtils.create(FaqItems)
            FaqItems.insert {
                it[id] = 1
                it[name] = "abcd"
            }
            FaqItems.insert {
                it[id] = 1
                it[name] = "cd"
            }
            val locateCd: Expression<Int> = FaqItems.name.locate("cd")

            val result = FaqItems.select(FaqItems.name, locateCd).map { it[locateCd] }

            assertEquals(2, result[0])
            assertEquals(0, result[1])

            SchemaUtils.drop(FaqItems)
        }
    }

    @Test
    fun testBig() {
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(Cities, Users)

            Cities.insert {
                it[name] = "St. Petersburg"
            }

            val munichId = Cities.insert {
                it[id] = ID++
                it[name] = "Munich"
            } get Cities.id

            val pragueId = Cities.insert {
                it[id] = ID++
                it.update(name, stringLiteral("   Prague   ").trim().substring(1, 2))
            }[Cities.id]

            val pragueName = Cities.selectAll().where { Cities.id eq pragueId }.single()[Cities.name]
            assertEquals(pragueName, "Pr")

            Users.insert {
                it[id] = "andrey"
                it[name] = "Andrey"
                it[cityId] = 5
            }

            Users.insert {
                it[id] = "sergey"
                it[name] = "Sergey"
                it[cityId] = munichId
            }

            Users.insert {
                it[id] = "eugene"
                it[name] = "Eugene"
                it[cityId] = munichId
            }

            Users.insert {
                it[id] = "alex"
                it[name] = "Alex"
                it[cityId] = null
            }

            Users.insert {
                it[id] = "smth"
                it[name] = "Something"
                it[cityId] = null
            }

            Users.update({ Users.id eq "alex" }) {
                it[name] = "Alexey"
            }

            Users.deleteWhere { name like "%thing" }

            println("All cities:")

            for (city in Cities.selectAll()) {
                println("${city[Cities.id]}: ${city[Cities.name]}")
            }

            println("Manual join:")
            (Users innerJoin Cities)
                .select(Users.name, Cities.name)
                .where {
                    (Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                        Users.id.eq("sergey") and Users.cityId.eq(Cities.id)
                }.forEach {
                    println("${it[Users.name]} lives in ${it[Cities.name]}")
                }

            println("Join with foreign key:")

            (Users innerJoin Cities)
                .select(Users.name, Users.cityId, Cities.name)
                .where { Cities.name.eq("St. Petersburg") or Users.cityId.isNull() }
                .forEach {
                    if (it[Users.cityId] != null) {
                        println("${it[Users.name]} lives in ${it[Cities.name]}")
                    } else {
                        println("${it[Users.name]} lives nowhere")
                    }
                }

            println("Functions and group by:")

            (
                (Cities innerJoin Users)
                    .select(Cities.name, Users.id.count())
                    .groupBy(Cities.name)
                ).forEach {
                val cityName = it[Cities.name]
                val userCount = it[Users.id.count()]

                if (userCount > 0) {
                    println("$userCount user(s) live(s) in $cityName")
                } else {
                    println("Nobody lives in $cityName")
                }
            }

            SchemaUtils.drop(Users, Cities)
        }
    }

    companion object {
        var ID = 1

        @BeforeClass
        @JvmStatic
        fun init() {
            Database.registerJdbcDriver("jdbc:ydb", "tech.ydb.jdbc.YdbDriver", "edikgoose.YdbDialect")
            Database.connect(
                "jdbc:ydb:grpc://localhost:2136/local",
                driver = "tech.ydb.jdbc.YdbDriver",
                databaseConfig = DatabaseConfig {
                    defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                    explicitDialect = YdbDialect()
                }
            )
            transaction {
            }
        }

        @AfterClass
        @JvmStatic
        fun clear() {
//            SchemaUtils.drop(FaqItems)
        }

        object FaqItems : Table("faq_item_test_2") {
            val id = long("id")
            val name = text("name")
            override val primaryKey = PrimaryKey(id, name = "faq_item_test_pk")
        }
    }
}
