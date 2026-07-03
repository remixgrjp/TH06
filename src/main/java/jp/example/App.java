/*
make
$ mvn clean compile
run
$ mvn exec:java -Dexec.mainClass=jp.example.App
*/
package jp.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;//for Logs
import java.util.Collections;//for Logs
import java.util.List;
import java.util.TreeMap;//for Logs

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;//for ResponseStatus

class TokenResponse{
	public Result result;
	public boolean success;
	public long t;

	public static class Result{
		public String access_token;
		public int expire_time;
		public String refresh_token;
		public String uid;
	}
}

class ResponseStatus{
	public List<Item> result;
	public boolean success;
	public long t;
	public String tid;
}
class Item{
	public String code;
	public JsonElement value;
}
class TH06Status{
	double temperature;
	int humidity;
	int battery;
	String unit;
	int tempSampling;
	int humiditySampling;

	@Override
	public String toString(){
		return String.format( "temperature:%.1f%s,humidity:%d%%,Battery:%d%%"
		, temperature, unit, humidity, battery );
	}

	TH06Status( ResponseStatus r )throws Exception{
		if( ! r.success ){
			throw new RuntimeException( "Status取得失敗" );
		}
		for( Item i : r.result ){
			switch( i.code ){
			  case "va_temperature":
				temperature= i.value.getAsInt() / 10.0;
				break;
			  case "va_humidity":
				humidity= i.value.getAsInt();
				break;
			  case "battery_percentage":
				battery= i.value.getAsInt();
				break;
			  case "temp_unit_convert":
				unit= i.value.getAsString();
				break;
			  case "temp_sampling":
				tempSampling = i.value.getAsInt();
				break;
			  case "humidity_sampling":
				humiditySampling = i.value.getAsInt();
				break;
			  default:
				System.out.println( "Unknown code [" + i.code + "]" );
				break;
			}
		}
	}
}

class ResponseLogs{
	public Result result;
	public boolean success;
	public long t;
	String tid;
}
class Result{
	String device_id;
	boolean has_more;
	String last_row_key;
	int total;
	List<LogItem> logs;
}
class LogItem{
	String code;
	long event_time;
	String value;
}

class TH06Log{
	long eventTime;
	Double temperature;
	Integer humidity;
}
class TH06Logs{
	List<TH06Log> list= new ArrayList<>();
//	TreeMap<Long, TH06Log> map= new TreeMap<>( Collections.reverseOrder() );

	TH06Logs( ResponseLogs r )throws Exception{
		TH06Log current= null;
		for( LogItem item : r.result.logs ){
			if( "va_temperature".equals( item.code) ){
				current= new TH06Log();
				current.eventTime= item.event_time;
				current.temperature= Integer.parseInt(item.value) / 10.0;
				list.add( current );
			}else if( "va_humidity".equals( item.code ) ){
				if( current != null ){
					current.humidity= Integer.parseInt( item.value );
				}
			}
		}
	}
//	TH06Logs( ResponseLogs r )throws Exception{
//		if( ! r.success ){
//			throw new RuntimeException( "Logs取得失敗" );
//		}
//		for( LogItem item : r.result.logs ){
//			TH06Log log= map.computeIfAbsent(
//				item.event_time,
//				t ->{
//					TH06Log x= new TH06Log();
//					x.eventTime= t;
//					return x;
//				}
//			);
//			switch( item.code ){
//			  case "va_temperature":
//				log.temperature= Integer.parseInt( item.value ) / 10.0;
//				break;
//			  case "va_humidity":
//				log.humidity= Integer.parseInt( item.value );
//				break;
//			}
//		}
//		list= new ArrayList<>( map.values() );
//	}
}

public class App{
	static void myLog( String s1, String s2 ){
		System.out.println( String.format( s1, s2 ) );
	}

	static String sha256( String s )throws Exception{
		MessageDigest md= MessageDigest.getInstance( "SHA-256" );
		byte[] hash= md.digest( s.getBytes( StandardCharsets.UTF_8 ) );

		StringBuilder sb= new StringBuilder();
		for( byte b : hash ){
			sb.append( String.format( "%02x", b ) );
		}
		return sb.toString();
	}

	static String hmacSha256( String text, String secret )throws Exception{
		Mac mac= Mac.getInstance( "HmacSHA256" );

		SecretKeySpec key= new SecretKeySpec(
			secret.getBytes( StandardCharsets.UTF_8 ),
			"HmacSHA256"
		);

		mac.init( key );
		byte[] bytes= mac.doFinal( text.getBytes( StandardCharsets.UTF_8 ) );

		StringBuilder sb= new StringBuilder();
		for( byte b : bytes ){
			sb.append( String.format( "%02X", b ) );
		}

		return sb.toString();
	}

	/* Low Level Get Token */
	static String callApiToken(
		String clientId,
		String accessSecret
	)throws Exception{
		String path= "/v1.0/token?grant_type=1";
		String bodyHash= sha256( "" );
		String stringToSign= "GET\n" +
			bodyHash
			+ "\n\n" 
			+ path;
		String t= String.valueOf( System.currentTimeMillis() );
		String signStr= clientId + t + stringToSign;
		String sign= hmacSha256( signStr, accessSecret );
		HttpRequest req= HttpRequest.newBuilder()
		.uri( new URI( "https://openapi.tuyaus.com"	+ path))
		.header( "client_id", clientId )
		.header( "t", t)
		.header( "sign_method", "HMAC-SHA256" )
		.header( "sign", sign )
		.GET()
		.build();

		HttpClient client= HttpClient.newHttpClient();

		HttpResponse<String> res= client.send( req, HttpResponse.BodyHandlers.ofString() );
		String s= res.body();
		myLog( "===Get Token (RAW)===\n%s\n===", s );
		return s;
	}

	static String buildStringToSign( String path )throws Exception{
		String bodyHash= sha256( "" );

		return "GET\n"
		+ bodyHash
		+ "\n\n"
		+ path;
	}

	/* Low Level Get Status */
	static String callApi(
		String clientId,
		String accessSecret,
		String accessToken,
		String path
	)throws Exception{
		myLog( "===path===\n%s\n===", path );
		String t= String.valueOf( System.currentTimeMillis() );
		String stringToSign= buildStringToSign( path );
		String signStr= clientId + accessToken + t + stringToSign;
		String sign= hmacSha256( signStr, accessSecret );

		HttpRequest req= HttpRequest.newBuilder()
		.uri( URI.create( "https://openapi.tuyaus.com" + path ) )
		.header( "client_id", clientId )
		.header( "access_token", accessToken )
		.header( "t", t )
		.header( "sign_method", "HMAC-SHA256" )
		.header( "sign", sign )
		.GET()
		.build();

		HttpClient client= HttpClient.newHttpClient();

		HttpResponse<String> res=
			client.send( req, HttpResponse.BodyHandlers.ofString() );
		String s= res.body();
		myLog( "===Get (RAW)===\n%s\n===", s );
		return s;
	}

	/* json Level Get AccessToken */
	static String getAccessToken(
		String clientId,
		String accessSecret
	)throws Exception{
		String json= callApiToken( clientId, accessSecret );

		Gson gson= new Gson();
		TokenResponse token= gson.fromJson( json, TokenResponse.class );
		if( ! token.success ){
			throw new RuntimeException( "Token取得失敗" );
		}
		myLog( "===access_token===\n%s\n===", token.result.access_token );
		return token.result.access_token;
	}

	/* json Level Get Status */
	static TH06Status getStatus(
		String clientId,
		String accessSecret,
		String accessToken,
		String deviceId
	)throws Exception{
		String path= "/v1.0/iot-03/devices/" + deviceId + "/status";
		String json= callApi( clientId, accessSecret, accessToken, path );

		Gson gson= new Gson();
		TH06Status o= new TH06Status( gson.fromJson( json, ResponseStatus.class ) );
		return o;
	}

	/* json Level Get Logs */
	static TH06Logs getLogs(
		String clientId,
		String accessSecret,
		String accessToken,
		String deviceId,
		LocalDateTime dtFm,
		LocalDateTime dtTo
	)throws Exception{
		String path= "/v2.0/cloud/thing/"
		+ deviceId
		+ "/report-logs?codes=va_temperature,va_humidity&end_time="
		+ getEpochMS( dtTo )	//"1782831600000" 2026-07-01 00:00:00
		+ "&size=20&start_time="
		+ getEpochMS( dtFm )	//"1780239600000" 2026-06-01 00:00:00
		;
		String json= callApi( clientId, accessSecret, accessToken, path );

		Gson gson= new Gson();
		TH06Logs o= new TH06Logs( gson.fromJson( json, ResponseLogs.class ) );
		return o;
	}

	static DateTimeFormatter dtf= DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:00" );
	static BufferedReader br;

	static String inputString( String str )throws IOException{
		System.out.print( "Input > " );
		String s= br.readLine().trim();
		if( ! s.isEmpty() ){
			str= s.split( "\\s+" )[0];//only first word
		}
		return str;
	}

	/* LocalDateTime -> long */
	static long getEpochMS( LocalDateTime dt ){
		return dt.atZone( ZoneId.systemDefault() ).toInstant().toEpochMilli();
	}

	static LocalDateTime getLocalDateTime( long l ){
		return LocalDateTime.ofInstant( java.time.Instant.ofEpochMilli( l ), ZoneId.systemDefault() );
	}

	static LocalDateTime inputDateTime( LocalDateTime ldt )throws IOException{
		System.out.print( "Input 20yy-MM-dd HH:mm > " );
		String s= br.readLine().trim();
		if( ! s.isEmpty() ){
			try{
				if( 10 == s.length() ){//入力文字列年月日のみを日時へ変換
					LocalDate date= LocalDate.parse( s );
					ldt= date.atStartOfDay();
				}else{//入力文字列年月日＋時刻を日時へ変換
					DateTimeFormatter formatter= DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm" );
					ldt= LocalDateTime.parse( s, formatter );
				}
			}catch( Exception e ){
				System.out.println( "NG. Format" );
			}
		}
		return ldt;
	}

	public static void main( String[] args )throws Exception{
		String CLIENT_ID    = "********************";
		String ACCESS_SECRET= "********************************";
		String DEVICE_ID    = "**********************";
		LocalDateTime dtTo= LocalDateTime.now().truncatedTo( java.time.temporal.ChronoUnit.SECONDS );
		LocalDateTime dtFm= dtTo.minusWeeks( 1 );
		String accessToken;

		br= new BufferedReader( new InputStreamReader( System.in ) );
		while( true ){
			System.out.println( "1: CLIENT_ID " + CLIENT_ID );
			System.out.println( "2: ACCESS_SECRET " + ACCESS_SECRET );
			System.out.println( "3: DEVICE_ID " + DEVICE_ID );
			System.out.println( "4: Get Token, Get the status of a single device." );
			System.out.println( "5: from " + dtFm.format( dtf ) + " (" + getEpochMS( dtFm ) + ")" );
			System.out.println( "6: to   " + dtTo.format( dtf ) + " (" + getEpochMS( dtTo ) + ")" );
			System.out.println( "7: Get Token, range data of device." );
			System.out.println( "Q: quit" );
			System.out.print( "> " );
			String s= br.readLine().trim();
			switch( s.toUpperCase() ){
			  case "1":
				CLIENT_ID= inputString( CLIENT_ID );
				break;
			  case "2":
				ACCESS_SECRET= inputString( ACCESS_SECRET );
				break;
			  case "3":
				DEVICE_ID= inputString( DEVICE_ID );
				break;
			  case "4":
				accessToken= getAccessToken( CLIENT_ID, ACCESS_SECRET );
				TH06Status o= getStatus( CLIENT_ID, ACCESS_SECRET, accessToken, DEVICE_ID );
				System.out.println( o.toString() );
				break;
			  case "5":
				dtFm= inputDateTime( dtFm );
				break;
			  case "6":
				dtTo= inputDateTime( dtTo );
				break;
			  case "7":
				accessToken= getAccessToken( CLIENT_ID, ACCESS_SECRET );
				TH06Logs j= getLogs( CLIENT_ID, ACCESS_SECRET, accessToken, DEVICE_ID, dtFm, dtTo );
				for( TH06Log i : j.list ){
					System.out.println( "" + i.eventTime + "," + getLocalDateTime( i.eventTime ).format( dtf ) + "," + i.temperature + "," + i.humidity );
				}
				break;
			  case "Q":
				System.out.println( "bye." );
				System.exit( 0 );
			  default:
				break;
			}
			System.out.println();
		}
	}
}
