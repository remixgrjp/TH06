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
import java.util.List;

public class App{
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

	/* long -> LocalDateTime */
	static LocalDateTime getLocalDateTime( long l ){
		return LocalDateTime.ofInstant( java.time.Instant.ofEpochMilli( l ), ZoneId.systemDefault() );
	}

	static LocalDateTime inputDateTime( LocalDateTime ldt )throws IOException{
		System.out.print( "Input 20yy-MM-dd HH:mm > " );
		String s= br.readLine().trim();
		if( ! s.isEmpty() ){
			try{
				if( 10 == s.length() ){//YYYY-MM-DD -> YYYY-MM-DD 00:00:00
					LocalDate date= LocalDate.parse( s );
					ldt= date.atStartOfDay();
				}else{//YYYY-MM-DD HH:MM:SS
					DateTimeFormatter formatter= DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm" );
					ldt= LocalDateTime.parse( s, formatter );
				}
			}catch( Exception e ){
				System.out.println( "NG. Format" );
			}
		}
		return ldt;
	}

	static void printCSV( List<History> l, String s ){
		System.out.println( s );//first line
		for( History i : l ){
			System.out.println( "" + i.eventTime + "," + getLocalDateTime( i.eventTime ).format( dtf ) + "," + i.value/10.0 );
		}
	}

	public static void main( String[] args )throws Exception{
		String ENDPOINT     = "https://openapi.tuyaus.com";
		String CLIENT_ID    = "********************";
		String ACCESS_SECRET= "********************************";
		String DEVICE_ID    = "**********************";
		LocalDateTime dt= LocalDateTime.now().truncatedTo( java.time.temporal.ChronoUnit.SECONDS );

		br= new BufferedReader( new InputStreamReader( System.in ) );
		TuyaClient client;
		List<History> list;
		while( true ){
			System.out.print( "0: URL: " + ENDPOINT
			+ "\n1: CLIENT_ID: " + CLIENT_ID
			+ "\n2: ACCESS_SECRET: " + ACCESS_SECRET
			+ "\n3: DEVICE_ID: " + DEVICE_ID
			+ "\n4: Get last status"
			+ "\n5: past->start_time-> [end_time] ->now: " + dt.format( dtf ) + " (" + getEpochMS( dt ) + ")"
			+ "\n6: Get log Temperature"
			+ "\n7: Get log Humidity"
			+ "\n8: Get log Battery"
			+ "\nQ: quit"
			+ "\n> " );
			String s= br.readLine().trim();
			try{
				switch( s.toUpperCase() ){
				  case "0":
					ENDPOINT= inputString( ENDPOINT );
					break;
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
					client= new TuyaClient( ENDPOINT, CLIENT_ID, ACCESS_SECRET, DEVICE_ID );
					TH06Status o= client.getStatus();
					System.out.println( o.toString() );
					break;
				  case "5":
					dt= inputDateTime( dt );
					break;
				  case "6":
					client= new TuyaClient( ENDPOINT, CLIENT_ID, ACCESS_SECRET, DEVICE_ID );
					list= client.getLogsTemperature( dt.minusWeeks( 1 ), dt );
					printCSV( list, "Time,DateTime,Temperature" );
					break;
				  case "7":
					client= new TuyaClient( ENDPOINT, CLIENT_ID, ACCESS_SECRET, DEVICE_ID );
					list= client.getLogsHumidity( dt.minusWeeks( 1 ), dt );
					printCSV( list, "Time,DateTime,Humidity" );
					break;
				  case "8":
					client= new TuyaClient( ENDPOINT, CLIENT_ID, ACCESS_SECRET, DEVICE_ID );
					list= client.getLogsBattery( dt.minusWeeks( 1 ), dt );
					printCSV( list, "Time,DateTime,Battery" );
					break;
				  case "Q":
					System.out.println( "bye." );
					System.exit( 0 );
				  default:
					System.out.println( "?" );
					break;
				}
			}catch( APIException e ){
				System.out.println( e.getMessage() );
			}
			System.out.println();
		}
	}
}