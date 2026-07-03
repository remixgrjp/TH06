/*
Get range(2026-06-18 13:15:51 JST - 2026-06-23 09:55:51 JST) data of device.

$ mvn clean compile
$ mvn dependency:copy-dependencies -DoutputDirectory=lib
$ java -cp target/classes:lib/* Sample2
*/
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;

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

public class Sample2{
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
		String stringToSign=
			"GET\n" +
			bodyHash + "\n" +
			"\n" +
			path;
		String t= String.valueOf( System.currentTimeMillis() );
		String signStr= clientId + t + stringToSign;
		String sign= hmacSha256( signStr, accessSecret );
		HttpRequest req= HttpRequest.newBuilder()
		.uri( new URI( "https://openapi.tuyaus.com"	+ path ) )
		.header( "client_id", clientId )
		.header( "t", t)
		.header( "sign_method", "HMAC-SHA256" )
		.header( "sign", sign )
		.GET()
		.build();

		HttpClient client= HttpClient.newHttpClient();

		HttpResponse<String> res= client.send( req, HttpResponse.BodyHandlers.ofString() );
		String s= res.body();
		myLog( "===Get Token===\n%s\n===", s );
		return s;
	}

	static String buildStringToSign( String path )throws Exception{
		String bodyHash= sha256( "" );

		return "GET\n"
		+ bodyHash
		+ "\n\n"
		+ path;
	}

	/* Low Level Get Logs */
	static String callApiStatus(
		String clientId,
		String accessSecret,
		String accessToken,
		String deviceId
	)throws Exception{
		String t= String.valueOf( System.currentTimeMillis() );
		String path=
			"/v2.0/cloud/thing/"
			+ deviceId
			+ "/report-logs?codes=va_temperature,va_humidity&end_time=1782250551235&size=20&start_time=1781830551235";
		String stringToSign= buildStringToSign( path );
		String signStr= clientId + accessToken + t + stringToSign;
		String sign= hmacSha256( signStr, accessSecret );

		HttpRequest req=
			HttpRequest.newBuilder()
				.uri( URI.create( "https://openapi.tuyaus.com" + path ) )
				.header( "client_id", clientId )
				.header( "access_token", accessToken )
				.header( "t", t )
				.header( "sign_method", "HMAC-SHA256" )
				.header( "sign", sign )
				.GET()
				.build();

		HttpClient client= HttpClient.newHttpClient();

		HttpResponse<String> res= client.send(
			req,
			HttpResponse.BodyHandlers.ofString()
		);
		String s= res.body();
		myLog( "===Get Status===\n%s\n===", s );
		return s;
	}

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

	public static void main( String[] args )throws Exception{
		String CLIENT_ID    = "********************";
		String ACCESS_SECRET= "********************************";
		String DEVICE_ID    = "**********************";

		String accessToken= getAccessToken( CLIENT_ID, ACCESS_SECRET );
		String statusJson= callApiStatus( CLIENT_ID, ACCESS_SECRET, accessToken, DEVICE_ID );
	}
}
