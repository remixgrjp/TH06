package jp.example;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;//for Logs
import java.util.List;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;//for ResponseStatus

class TuyaResponse{
	boolean success;
	long t;
	String tid;
//	each unique Response Class
	Integer code;//成功時は存在しないので !int
	String msg;
}

class ResponseToken extends TuyaResponse{
	Result result;

	static class Result{
		String access_token;
		int expire_time;
		String refresh_token;
		String uid;
	}
}
class TH06Token{
	String access_token;

	TH06Token( ResponseToken r )throws Exception{
		if( ! r.success ){
			throw new RuntimeException( r.code + ":" + r.msg );
		}
		access_token= r.result.access_token;
	}
}

class ResponseStatus extends TuyaResponse{
	List<Item> result;
}
class Item{
	String code;
	JsonElement value;
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
			throw new RuntimeException( r.code + ":" + r.msg );
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

class ResponseLogs extends TuyaResponse{
	Result result;
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

class HistoryValue{
	long eventTime;
	int value;
	HistoryValue( long l, String s )throws Exception{
		eventTime= l;
		value= Integer.parseInt( s );
	}
}
class TH06Logs{
	List<HistoryValue> listT= new ArrayList<>();
	List<HistoryValue> listH= new ArrayList<>();
	List<HistoryValue> listB= new ArrayList<>();

	TH06Logs( ResponseLogs r ){
		if( ! r.success ){
			throw new RuntimeException( r.code + ":" + r.msg );
		}
		for( LogItem i : r.result.logs ){
			try{
				switch( i.code ){
				  case "va_temperature"://*10
					listT.add( new HistoryValue( i.event_time, i.value ) );
					break;
				  case "va_humidity"://%
					listH.add( new HistoryValue( i.event_time, i.value ) );
					break;
				  case "battery_percentage"://%
					listB.add( new HistoryValue( i.event_time, i.value ) );
					break;
				}
			}catch( Exception e ){
				System.out.println( "Skiped a data [" + i.value + "]." );
			}
		}
	}
}

public class TuyaClient{
	String clientId;
	String accessSecret;
	String deviceId;
	String accessToken;

	TuyaClient( String s1, String s2, String s3 ){
		clientId= s1;
		accessSecret= s2;
		deviceId= s3;
	}

	/* json Level Get Status */
	TH06Status getStatus()throws Exception{
		accessToken= getAccessToken( clientId, accessSecret );
		return getStatus( clientId, accessSecret, accessToken, deviceId );
	}

	/* json Level Get Logs */
	TH06Logs getLogs( LocalDateTime dtStart, LocalDateTime dtEnd )throws Exception{
		accessToken= getAccessToken( clientId, accessSecret );
		return getLogs( clientId, accessSecret, accessToken, deviceId, dtStart, dtEnd );
	}

	static void myLog( String s1, String s2 ){
		System.out.println( String.format( s1, s2 ) );
	}

	/* LocalDateTime -> long */
	static long getEpochMS( LocalDateTime dt ){
		return dt.atZone( ZoneId.systemDefault() ).toInstant().toEpochMilli();
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

	/* Low Level API Get Token */
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

	/* Low Level API Get */
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

		HttpResponse<String> res= client.send( req, HttpResponse.BodyHandlers.ofString() );
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
		TH06Token o= new TH06Token( gson.fromJson( json, ResponseToken.class ) );
		return o.access_token;
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
		LocalDateTime dtStart,
		LocalDateTime dtEnd
	)throws Exception{
		String path= "/v2.0/cloud/thing/"
		+ deviceId
		+ "/report-logs?codes=va_temperature,va_humidity,battery_percentage&end_time="
		+ getEpochMS( dtEnd )   // 1782831600000 / 2026-07-01 00:00:00
		+ "&size=100&start_time="// descending DateTime limit 100
		+ getEpochMS( dtStart ) // 1780239600000 / 2026-06-01 00:00:00
		;
		String json= callApi( clientId, accessSecret, accessToken, path );

		Gson gson= new Gson();
		TH06Logs o= new TH06Logs( gson.fromJson( json, ResponseLogs.class ) );
		return o;
	}
}