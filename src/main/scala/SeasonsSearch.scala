import scala.io.Source
import scala.util.parsing.json.JSON

import org.scalatra._
import scalate.ScalateSupport
import com.mongodb.casbah.Imports._
import scala.collection.mutable.ArrayBuffer

import java.awt.Color

class SeasonsSearch extends ScalatraServlet with ScalateSupport {
  //setup Casbah connection
  val mongo = MongoConnection("localhost",27017)("seasons")("geo")

  get("/") {
    //get current geo info
    //とりあえず user name固定
    var name = "yukondou"
    params.get("name").foreach(name = _)
    var curLat = ""
    var curLon = ""
    mongo.find(MongoDBObject("name" -> name)).sort(MongoDBObject("timestamp" -> -1)).limit(1).foreach(geo => {
      println(geo)
      curLat = geo("lat").toString()
      curLon = geo("lon").toString()
    })

    //get recommended parameters
    var recomLatLonArray = new ArrayBuffer[Map[String,String]]()
    //val seasonsApi = "http://under-hair.com:8124/"
    
    println(mongo.find(MongoDBObject("name" -> name)).size)
    val seasonsApi = "http://localhost:8124/"
    val seasonsRes = JSON.parseFull(Source.fromURL(seasonsApi, "utf-8").getLines.mkString)
    val seasonsResult = seasonsRes.get.asInstanceOf[Map[String,List[Map[String,Any]]]]
    seasonsResult("station").take(4).foreach(station => {
      println(station("Name"))
      val geo = station("Geometry").asInstanceOf[Map[String,String]]
      val latlon = geo("Coordinates").split(",")
      recomLatLonArray += Map[String,String](
        "lat" -> latlon(1),
        "lon" -> latlon(0)
      )
    })
    //val result = seasonsRes.get.asInstanceOf[Map[String,List[Map[String,Any]]]]
    //val searchResult = new ArrayBuffer[Map[String,String]]()
    /*
    recomLatLonArray += Map[String,String](
      "lat" -> "35.658517",
      "lon" -> "139.701334"
    )
    recomLatLonArray += Map[String,String](
      "lat" -> "35.64669",
      "lon" -> "139.710106"
    )
    */

    def getSearchResult(lat:String, lon:String):ArrayBuffer[Map[String,String]] = {
      //local search
      var url = "http://search.olp.yahooapis.jp/OpenLocalPlatform/V1/localSearch?appid=cV8qsbmxg67L0Z7MV1B7vtwGTL5uf2wHPQhZPkam8Wfjp_.7SpgzAEn9cID00NXUcpqY" + "&output=json&gc=01&sort=dist&lat=" + lat + "&lon=" + lon + "&dist=800" + "query="
      params.get("keyword").foreach(keyword => {
        url += java.net.URLEncoder.encode(keyword, "UTF-8")
      })
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
      return searchResult
    }

    //
    //search
    //
    var searchResultSet = new ArrayBuffer[ArrayBuffer[Map[String,String]]]
    //推薦緯度経度がない場合、最新の緯度経度で検索する
    if (!recomLatLonArray.isEmpty) {
      recomLatLonArray.foreach(recom => {
        searchResultSet += getSearchResult(recom("lat"), recom("lon"))
      })
    } else if (curLat != "" && curLon != "") {
      searchResultSet += getSearchResult (curLat, curLon)
    }
    //make static map
    //現在地情報が取得できる場合は、現在地を表示
    //検索対象の緯度経度がある場合は、緯度経度と範囲を表示
    //検索結果も地図表示
    val ysm = new YolpStaticMap("cV8qsbmxg67L0Z7MV1B7vtwGTL5uf2wHPQhZPkam8Wfjp_.7SpgzAEn9cID00NXUcpqY",
      //"35.658619279712", "139.74553000746",
      320, 280)

    if (curLat != "" && curLon != "") {
      ysm.addPin(curLat, curLon, "default")
    } else {
      println("cannot get current geo info")
    }

    recomLatLonArray.foreach(recom => {
      ysm.addCircle(new Color(255, 0, 0), 0, 1, new Color(255, 0, 0), 80, recom("lat"), recom("lon"), 800)
    })

    searchResultSet.foreach(sr => {
      sr.foreach(r => {
        ysm.addPin(r("Lat"), r("Lon"), "")
      })
    })

    var mapUrl = ""
    if (ysm.haveCircles || ysm.havePins) {
      mapUrl = ysm.getUrl
    }

    contentType = "text/html"
    templateEngine.layout("WEB-INF/layouts/main.ssp",
      Map(
        "searchResultSet" -> searchResultSet,
        "staticMap" -> mapUrl,
        "name" -> name
      ))
  }

  post("/") {
    val builder = MongoDBObject.newBuilder

    //save to mongo
    params.get("lat").foreach(lat => {builder += ("lat" -> lat)})
    params.get("lon").foreach(lon => {builder += ("lon" -> lon)})
    params.get("name").foreach(name => {builder += ("name" -> name)})
    builder += ("timestamp" -> java.util.Calendar.getInstance().getTimeInMillis())
    mongo += builder.result

    var url = "/"
    var newParams = new ArrayBuffer[String]
    params.get("keyword").foreach(keyword => if (keyword != "") {newParams += "keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8")})
    params.get("name").foreach(name => newParams += "name=" + java.net.URLEncoder.encode(name, "UTF-8"))
    if (!newParams.isEmpty) {
      url += "?" + newParams.mkString("&")
    }
    redirect(url)
  }

  get("/test") {
    val name = "yukondou"
    //$ date +%s -d '2012/02/20 11:30:00'
    val baseTime:Int = 1329705000
    val testDataSet = List(
      //品川
      ("35.630152,139.74044",baseTime),
      //大崎
      ("35.6197,139.728553",(baseTime + 120).toString()),
      //五反田
      ("35.626446,139.723444", (baseTime + 300).toString()),
      //目黒
      ("35.633998,139.715828",(baseTime + 420).toString()),
      //恵比寿
      ("35.64669,139.710106",(baseTime + 600).toString()),
      //渋谷
      ("35.658517,139.701334",(baseTime + 720).toString()),
      //原宿
      ("35.670168,139.702687",(baseTime + 840).toString()),
      //代々木
      ("35.683061,139.702042",(baseTime + 1020).toString()),
      //新宿
      ("35.690921,139.700258",(baseTime + 1140).toString())
    )

    var num:Int = 0
    params.get("n").foreach(n => {
      num = n.toInt
    })

    /*
    testDataSet.foreach(data => {
      //save to mongo
      val builder = MongoDBObject.newBuilder
      val latlon = data._1.split(",")
      builder += ("lat" -> latlon(0))
      builder += ("lon" -> latlon(1))
      builder += ("name" -> name)
      builder += ("timestamp" -> data._2)
      mongo += builder.result
    })
    */
    val data = testDataSet(num)
    val builder = MongoDBObject.newBuilder
    val latlon = data._1.split(",")
    builder += ("lat" -> latlon(0))
    builder += ("lon" -> latlon(1))
    builder += ("name" -> name)
    builder += ("timestamp" -> data._2)
    mongo += builder.result

    var result = new ArrayBuffer[String]()
    mongo.foreach(geo => {
      result += List(geo.get("name"), geo.get("lat"), geo.get("lon"), geo.get("timestamp")).mkString(", ")
    })

    contentType = "text/html"
    templateEngine.layout("WEB-INF/layouts/list.ssp", Map("result" -> result))
  }

    get("/test2") {
      val name = "yukondou"
      //$ date +%s -d '2012/02/20 11:30:00'
      val baseTime:Int = 1329705000
      val testDataSet = List(
        //六本木
        ("35.664068,139.731277",baseTime),
        //青山一丁目
        ("35.672841,139.724018",(baseTime + 120).toString()),
        //国立競技場
        ("35.679892,139.714721", (baseTime + 300).toString()),
        //代々木
        ("35.683777,139.701527", (baseTime + 480).toString()),
        //新宿
        ("35.687621,139.699347", (baseTime + 540).toString())
      )

      var num:Int = 0
      params.get("n").foreach(n => {
        num = n.toInt
      })

      /*
      testDataSet.take(num).foreach(data => {
        //save to mongo
        val builder = MongoDBObject.newBuilder
        val latlon = data._1.split(",")
        builder += ("lat" -> latlon(0))
        builder += ("lon" -> latlon(1))
        builder += ("name" -> name)
        builder += ("timestamp" -> data._2)
        mongo += builder.result
      })
      */

      //save to mongo
      val data = testDataSet(num)
      val builder = MongoDBObject.newBuilder
      val latlon = data._1.split(",")
      builder += ("lat" -> latlon(0))
      builder += ("lon" -> latlon(1))
      builder += ("name" -> name)
      builder += ("timestamp" -> data._2)
      mongo += builder.result
      
      var result = new ArrayBuffer[String]()
      mongo.foreach(geo => {
        result += List(geo.get("name"), geo.get("lat"), geo.get("lon"), geo.get("timestamp")).mkString(", ")
      })

      contentType = "text/html"
      templateEngine.layout("WEB-INF/layouts/list.ssp", Map("result" -> result))
  }

}

class YolpStaticMap(
  private val appid: String,
  //private val lat: String,
  //private val lon: String,
  private val width: Int,
  private val height: Int
) {
  private val baseUrl = "http://map.olp.yahooapis.jp/OpenLocalPlatform/V1/static"
  private var pins = new ArrayBuffer[String]
  private var circles = new ArrayBuffer[String]
  private val default = "scalebar=off&logo=off&output=png&quality=40&z=14&mode=map&style=base:vivid"
  def addPin(lat:String, lon:String, style:String = "", label:String = "", color:String = "") = {
    pins += "pin%s=%s,%s,%s,%s".format(style, lat, lon, label, color)
  }
  def addCircle(borderColor:Color, borderAlpha:Int = 0, borderWidth:Int = 1,
                innerColor:Color, innerAlpha:Int = 0,
                lat:String, lon:String, dist:Int) = {
    var tmp = new ArrayBuffer[String]
    tmp += borderColor.getRed().toString()
    tmp += borderColor.getGreen().toString()
    tmp += borderColor.getBlue().toString()
    tmp += borderAlpha.toString()
    tmp += borderWidth.toString()
    tmp += innerColor.getRed().toString()
    tmp += innerColor.getGreen().toString()
    tmp += innerColor.getBlue().toString()
    tmp += innerAlpha.toString()
    tmp += lat
    tmp += lon
    tmp += dist.toString()
    circles += tmp.mkString(",")
  }
  def havePins = !pins.isEmpty
  def haveCircles = !circles.isEmpty
  def getParameterString = {
    var tmp = new ArrayBuffer[String]
    tmp += "appid=" + appid
    //tmp += "lat=" + lat
    //tmp += "lon=" + lon
    tmp += "width=" + width
    tmp += "height=" + height
    tmp += default
    val pin = pins.mkString("&")
    if (pin != "") {
      tmp += pin
    }
    val circle = circles.mkString(":")
    if (circle != "") {
      tmp += "e=" + circle
    }
    tmp.mkString("&")
  }
  def getUrl = baseUrl + "?" + getParameterString
}

object YolpStaticMap {
  def main(args: Array[String]) {
    val ysm = new YolpStaticMap("cV8qsbmxg67L0Z7MV1B7vtwGTL5uf2wHPQhZPkam8Wfjp_.7SpgzAEn9cID00NXUcpqY",
                                //"35.658619279712", "139.74553000746",
                                200, 200)
    ysm.addPin("35.658619279712", "139.74553000746", "")
    ysm.addCircle(new Color(255, 0, 0), 0, 1, new Color(255, 0, 0), 127, "35.658619279712", "139.74553000746", 3000)
    ysm.addPin("35.758619279812", "139.74553000750", "")
    ysm.addCircle(new Color(255, 0, 0), 0, 1, new Color(255, 0, 0), 127, "35.758619279812", "139.74553000750", 3000)
    println(ysm.getUrl)
  }
}


