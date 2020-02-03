package zio.actors.persistence

import java.io.File

import zio.Task
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor._

private[actors] object Utils {

  case class DbURL(value: String)  extends AnyVal
  case class DbUser(value: String) extends AnyVal
  case class DbPass(value: String) extends AnyVal
  case class DbConfig(dbURL: DbURL, dbUser: DbUser, dbPass: DbPass)

  case class JournalPlugin(value: String)
  case class MyConfig(ldap: String)

  val pluginConfig: ConfigDescriptor[String, String, JournalPlugin] =
    nested("persistence") {
      string("plugin").xmap(JournalPlugin)(_.value)
    }

  val dbConfig: ConfigDescriptor[String, String, DbConfig] =
    nested("persistence") {
      (string("url").xmap(DbURL)(_.value) |@|
        string("user").xmap(DbUser)(_.value) |@|
        string("pass").xmap(DbPass)(_.value))(DbConfig.apply, DbConfig.unapply)
    }

  def getPluginChoice(systemName: String, configFile: File): Task[JournalPlugin] =
    zio.actors.Utils.getConfig(systemName, configFile, pluginConfig)

  def getDbConfig(systemName: String, configFile: File): Task[DbConfig] =
    zio.actors.Utils.getConfig(systemName, configFile, dbConfig)

}
