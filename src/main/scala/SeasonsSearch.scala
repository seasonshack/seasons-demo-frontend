import org.scalatra._
import scalate.ScalateSupport
import com.mongodb.casbah.Imports._
import scala.collection.mutable.ArrayBuffer

class SeasonsSearch extends ScalatraServlet with ScalateSupport {
  //setup Casbah connection
  val mongo = MongoConnection("localhost",27017)("seasons")("geo")

  get("/") {
    var result = new ArrayBuffer[String]()
    mongo.foreach(geo => {
      result += List(geo.get("name"), geo.get("lat"), geo.get("lon"), geo.get("timestamp")).mkString(", ")
    }
    )

    contentType = "text/html"
    templateEngine.layout("WEB-INF/layouts/main.ssp", Map("result" -> result))
  }

  post("/") {
    val builder = MongoDBObject.newBuilder
    params.get("lat").foreach(lat => {builder += ("lat" -> lat)})
    params.get("lon").foreach(lon => {builder += ("lon" -> lon)})
    params.get("name").foreach(name => {builder += ("name" -> name)})
    builder += ("timestamp" -> java.util.Calendar.getInstance().getTimeInMillis())
    mongo += builder.result
  }

  get("/list") {
    var geoArray = new ArrayBuffer[Map[String,String]]()
    mongo.foreach(geo => {
      val d = Map[String,String](
        "Img" -> ("http://map.olp.yahooapis.jp/OpenLocalPlatform/V1/static?appid=cV8qsbmxg67L0Z7MV1B7vtwGTL5uf2wHPQhZPkam8Wfjp_.7SpgzAEn9cID00NXUcpqY&pin1=" + List(geo.get("lat"), geo.get("lon")).mkString(",") + ",Tower&z=17&width=100&height=100").toString(),
        "Str" -> List(geo.get("name"), geo.get("lat"), geo.get("lon"), geo.get("timestamp")).mkString(", ")
      )
      geoArray += d
    })
    contentType = "text/html"
    templateEngine.layout("WEB-INF/layouts/list.ssp", 
                          Map(
                            "geoArray" -> geoArray
                          ))
  }
  
  get("/msgs") {
    <body>
      <ul>
        {for (msg <- mongo) yield <li>{msg.get("lat")}</li>}
      </ul>
      <form method="POST" action="/msgs">
        <input type="text" name="body"/>
        <input type="submit"/>
      </form>
    </body>  
  }

}
