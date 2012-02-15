import scala.io.Source
import scala.util.parsing.json.JSON

import org.scalatra._
import scalate.ScalateSupport
import com.mongodb.casbah.Imports._
import scala.collection.mutable.ArrayBuffer

class SeasonsSearch extends ScalatraServlet with ScalateSupport {
  //setup Casbah connection
  val mongo = MongoConnection("localhost",27017)("seasons")("geo")

  get("/") {
    /*
    var result = new ArrayBuffer[String]()
    mongo.foreach(geo => {
      result += List(geo.get("name"), geo.get("lat"), geo.get("lon"), geo.get("timestamp")).mkString(", ")
    }
    )
    */

    //local search
    var url = "http://search.olp.yahooapis.jp/OpenLocalPlatform/V1/localSearch?appid=cV8qsbmxg67L0Z7MV1B7vtwGTL5uf2wHPQhZPkam8Wfjp_.7SpgzAEn9cID00NXUcpqY&output=json&gc=01&ac=13103&sort=hybrid&query="
    params.get("keyword").foreach(keyword => {url += java.net.URLEncoder.encode(keyword, "UTF-8")})
    println(url)
    val source = Source.fromURL(url, "utf-8")
    val response = JSON.parseFull(source.getLines.mkString)
    val result = response.get.asInstanceOf[Map[String,List[Map[String,Any]]]]
    val searchResult = new ArrayBuffer[Map[String,String]]()
    result("Feature").foreach(feature => {
      //get image info
      var img:String = ""
      feature("Property").asInstanceOf[Map[String,String]].get("LeadImage") match {
        case Some(v) => img = v
        case None => img = "/img/noimage.jpeg"
      }

      //get station info
      var st:String = ""
      feature("Property").asInstanceOf[Map[String,Any]].get("Station").foreach(stationList => {
        val station = stationList.asInstanceOf[List[Map[String,String]]].head
        //var tmp = Nil
        station("Name") match {
          case n:String => st += n
          case _ => ""
        }
        station("Railway") match {
          case r:String => st += "/" + r
          case _ => ""
        }
      })
      
      //get latlon
      val g = feature("Geometry").asInstanceOf[Map[String,String]]
      val coordinates = g("Coordinates").split(",")
      val lat = coordinates(1)
      val lon = coordinates(0)

      //make result hash
      val r = Map[String,String](
        "Name" -> feature("Name").toString,
        "Img" -> img,
        "Station" -> st,
        "Lat" -> lat,
        "Lon" -> lon
      )
      searchResult += r
    })

    var pins = new ArrayBuffer[String]
    var i = 1
    searchResult.foreach(r => {
      pins += "pin%d=%s,%s".format(i, r("Lat"), r("Lon"))
      i += 1
    })
    val staticMap = "http://map.olp.yahooapis.jp/OpenLocalPlatform/V1/static?appid=cV8qsbmxg67L0Z7MV1B7vtwGTL5uf2wHPQhZPkam8Wfjp_.7SpgzAEn9cID00NXUcpqY&width=230&height=200&scalebar=off&logo=off&output=png&quality=40&maxzoom=17&" +pins.mkString("&")

    contentType = "text/html"
    templateEngine.layout("WEB-INF/layouts/main.ssp", Map("searchResult" -> searchResult, "staticMap" -> staticMap))
  }

  post("/") {
    val builder = MongoDBObject.newBuilder

    //save to mongo
    params.get("lat").foreach(lat => {builder += ("lat" -> lat)})
    params.get("lon").foreach(lon => {builder += ("lon" -> lon)})
    params.get("name").foreach(name => {builder += ("name" -> name)})
    builder += ("timestamp" -> java.util.Calendar.getInstance().getTimeInMillis())
    mongo += builder.result

    //redirect("/")
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

}
