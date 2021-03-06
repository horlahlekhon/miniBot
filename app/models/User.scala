package models
import javax.inject.Inject
import org.mindrot.jbcrypt.BCrypt
import play.api.data.Form
import play.api.libs.json.{JsObject, Json, OFormat}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.bson.Macros.Annotations.Key
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}


case class User(name: String, @Key("_id") id: BSONObjectID = BSONObjectID.generate(), title: String, password: String)

object UserJsonFormat {
  implicit val userFormat: OFormat[User] = Json.format[User]

}
case class RegisterUser(name:String, password: String, title: String)
case class Login(name: String, password: String)
object User{
  import play.api.data.Forms._

  val registerForm: Form[RegisterUser] = Form (
    mapping(
      "username" -> nonEmptyText(minLength = 2, maxLength = 20),
      "password" -> nonEmptyText(minLength = 4),
      "title" -> nonEmptyText(maxLength = 3, minLength = 2)
    )(RegisterUser.apply)(RegisterUser.unapply)
  )


  val loginForm: Form[Login] = Form(
    mapping(
      "username" -> nonEmptyText(minLength = 2),
      "password" -> nonEmptyText(minLength = 4)
    )(Login.apply)(Login.unapply)
  )
}

class UserRepository @Inject()(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi){

  import UserJsonFormat._

  val userCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("user"))

  def getAllUsers: Future[Seq[User]] = {
    userCollection.flatMap {
      _.find(
        selector = Json.obj(),
        projection = Option.empty[JsObject])
        .cursor[User](ReadPreference.primary)
        .collect[Seq](maxDocs = 100, err = Cursor.FailOnError[Seq[User]]())
    }
  }

  def createUser(user: User): Future[Option[User]] ={
   getUser(user.name).map{
     case Some(value) =>
        Some(value)
     case None =>
       userCollection.flatMap(
         _.insert(ordered = false)
           .one(user.copy(password = BCrypt.hashpw(user.password, BCrypt.gensalt()))))
       None
   }
  }


  def getUser(name: String): Future[Option[User]] = {
    userCollection.flatMap(_.find(
      BSONDocument("name" -> name))
      .one[User])
  }


  def auth(name: String, pass:String): Future[Option[User]] = {
    getUser(name).map{
      case Some(value: User) =>
        if (BCrypt.checkpw(pass, value.password)) Some(value) else None
      case _ =>
        None
    }
  }
}