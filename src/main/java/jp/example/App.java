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
		LocalDateTime dt= LocalDateTime.now().truncatedTo( java.time.temporal.ChronoUnit.SECONDS );

		br= new BufferedReader( new InputStreamReader( System.in ) );
		while( true ){
			System.out.print( "1: CLIENT_ID: " + CLIENT_ID
			+ "\n2: ACCESS_SECRET: " + ACCESS_SECRET
			+ "\n3: DEVICE_ID: " + DEVICE_ID
			+ "\n4: Get Token, Get the status"
			+ "\n5: past->start_time-> [end_time] ->now: " + dt.format( dtf ) + " (" + getEpochMS( dt ) + ")"
			+ "\n6: Get Token, Get range of Log"
			+ "\nQ: quit"
			+ "\n> " );
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
				try{
					TuyaClient c= new TuyaClient( CLIENT_ID, ACCESS_SECRET, DEVICE_ID );
					TH06Status o= c.getStatus();
					System.out.println( o.toString() );
				}catch( RuntimeException e ){
					System.out.println( "NG get status[" + e.getMessage() + "]" );
				}
				break;
			  case "5":
				dt= inputDateTime( dt );
				break;
			  case "6":
				try{
					TuyaClient c= new TuyaClient( CLIENT_ID, ACCESS_SECRET, DEVICE_ID );
					TH06Logs j= c.getLogs( dt.minusWeeks( 1 ), dt );
					System.out.println( "Time,DateTime,temperature" );
					for( HistoryValue i : j.listT )
						System.out.println( "" + i.eventTime + "," + getLocalDateTime( i.eventTime ).format( dtf ) + "," + i.value/10.0 );
					System.out.println( "Time,DateTime,humidity" );
					for( HistoryValue i : j.listH )
						System.out.println( "" + i.eventTime + "," + getLocalDateTime( i.eventTime ).format( dtf ) + "," + i.value );
					System.out.println( "Time,DateTime,Battery" );
					for( HistoryValue i : j.listB )
						System.out.println( "" + i.eventTime + "," + getLocalDateTime( i.eventTime ).format( dtf ) + "," + i.value );
				}catch( RuntimeException e ){
					System.out.println( "NG get logs[" + e.getMessage() + "]" );
				}
				break;
			  case "Q":
				System.out.println( "bye." );
				System.exit( 0 );
			  default:
				System.out.println( "?" );
				break;
			}
			System.out.println();
		}
	}
}