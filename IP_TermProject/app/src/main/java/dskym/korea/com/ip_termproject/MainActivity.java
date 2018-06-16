package dskym.korea.com.ip_termproject;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;

public class MainActivity extends Activity implements View.OnClickListener{

    SNMPAsyncTask asyncTask;

    TextView singleTextView;
    TextView multiTextView;

    EditText oidEditText;
    EditText valueEditText;

    Button snmpgetButton;
    Button snmpsetButton;
    Button snmpwalkButton;

    Spinner dataTypeSpinner;
    ArrayAdapter dataTypeAdapter;

    //어플리케이션에서 사용하는 요소들을 변수에 할당
    public void init() {
        singleTextView = (TextView)findViewById(R.id.singleTextView);

        multiTextView = (TextView)findViewById(R.id.multiTextView);
        multiTextView.setMovementMethod(new ScrollingMovementMethod());

        oidEditText = (EditText)findViewById(R.id.oid);
        valueEditText = (EditText)findViewById(R.id.value);

        snmpgetButton = (Button)findViewById(R.id.snmpgetButton);
        snmpsetButton = (Button)findViewById(R.id.snmpsetButton);
        snmpwalkButton = (Button)findViewById(R.id.snmpwalkButton);

        dataTypeSpinner = (Spinner)findViewById(R.id.dataTypeSpinner);
        dataTypeAdapter = ArrayAdapter.createFromResource(this,
                R.array.dataType, android.R.layout.simple_spinner_item);
        dataTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dataTypeSpinner.setAdapter(dataTypeAdapter);

        snmpgetButton.setOnClickListener(this);
        snmpsetButton.setOnClickListener(this);
        snmpwalkButton.setOnClickListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    //버튼 클릭 시 호출
    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.snmpgetButton) {
            String oid = oidEditText.getText().toString();

            asyncTask = new SNMPAsyncTask();

            asyncTask.execute("GET", oid);
        }
        else if(v.getId() == R.id.snmpsetButton) {
            String oid = oidEditText.getText().toString();
            String value = valueEditText.getText().toString();
            String type = dataTypeSpinner.getSelectedItem().toString();

            asyncTask = new SNMPAsyncTask();

            asyncTask.execute("SET", oid, type, value);
        }
        else if(v.getId() == R.id.snmpwalkButton) {
            String oid = oidEditText.getText().toString();

            multiTextView.setText("");

            asyncTask = new SNMPAsyncTask();

            asyncTask.execute("GETWALK", oid);
        }
    }

    //네트워크 통신을 위한 스레드
    class SNMPAsyncTask extends AsyncTask<String, Void, String> {
        String host = "kuwiden.iptime.org";
        int port = 11161;

        InetAddress ip;
        DatagramSocket socket;
        DatagramPacket sendPacket;
        DatagramPacket receivePacket;

        byte[] requestPacket;
        byte[] responsePacket = new byte[512];

        int[] oid;
        byte[] community = "public".getBytes();
        byte[] writeCoummunity = "write".getBytes();

        String result;

        String command;
        String oids;
        String type;
        String value;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        //execute 함수 호출 시 실행
        @Override
        protected String doInBackground(String... params) {
            try {
                ip = InetAddress.getByName(host);

                socket = new DatagramSocket();

                command = params[0];
                oids = params[1];

                Log.d("TAG", oids);

                value="";
                type="";

                if(params.length == 4) {
                    type = params[2];
                    value = params[3];
                }

                socket.connect(ip, port);

                //SNMP GET 명령
                if(command.equals("GET")) {
                    //SNMP GET 요청 패킷 생성
                    requestPacket = makeGetRequestPacket(oids);

                    sendPacket = new DatagramPacket(requestPacket, requestPacket.length);

                    //SNMP GET 요청 패킷 전송
                    socket.send(sendPacket);

                    receivePacket = new DatagramPacket(responsePacket, responsePacket.length);

                    //SNMP GET 응답 패킷 수신
                    socket.receive(receivePacket);

                    //SNMP GET 응답 패킷 분석
                    String[] temp = decodeResponsePacket(receivePacket.getData());

                    oids = temp[0];

                    if(temp[3].equals("0"))
                        result = printResult(temp);
                    else if(temp[3].equals("1"))
                        result = printErrorResult(temp);
                }
                //SNMP SET 명령
                else if(command.equals("SET")) {
                    //SNMP SET 요청 패킷 생성
                    requestPacket = makeSetRequestPacket(oids, type, value);

                    sendPacket = new DatagramPacket(requestPacket, requestPacket.length);

                    //SNMP SET 요청 패킷 전송
                    socket.send(sendPacket);

                    receivePacket = new DatagramPacket(responsePacket, responsePacket.length);

                    //SNMP SET 응답 패킷 수신
                    socket.receive(receivePacket);

                    //SNMP SET 응답 패킷 분석
                    String[] temp = decodeResponsePacket(receivePacket.getData());

                    oids = temp[0];

                    if(temp[3].equals("0"))
                        result = printResult(temp);
                    else if(temp[3].equals("1"))
                        result = printErrorResult(temp);
                }
                //SNMP GETWALK 명령
                else if(command.equals("GETWALK")) {
                    while(true) {
                        //SNMP GETNEXT 요청 패킷 생성
                        requestPacket = makeGetWalkRequestPacket(oids);

                        sendPacket = new DatagramPacket(requestPacket, requestPacket.length, ip, port);

                        //SNMP GETNEXT 요청 패킷 전송
                        socket.send(sendPacket);

                        receivePacket = new DatagramPacket(responsePacket, responsePacket.length, ip, port);

                        //SNMP GETNEXT 응답 패킷 수신
                        socket.receive(receivePacket);

                        //SNMP GETNEXT 응답 패킷 분석
                        String[] temp = decodeResponsePacket(receivePacket.getData());

                        oids = temp[0];

                        if(temp[3].equals("0"))
                            result = printResult(temp);
                        else if(temp[3].equals("1"))
                            result = printErrorResult(temp);

                        Log.d("TAG", result);

                        //결과 UI 반영
                        publishProgress();

                        //SNMP GETWALK 완료
                        if(temp[2].equals("endOfMibView"))
                            break;
                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketException se) {
                se.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            return result;
        }

        //UI 업데이트 수행
        @Override
        protected void onProgressUpdate(Void... params) {
            super.onProgressUpdate(params);

            multiTextView.append(result);
        }

        //스레드 실행 종료 후 결과 UI 출력
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if(command.equals("GET"))
                singleTextView.setText(result);
            else if(command.equals("SET"))
                singleTextView.setText(result);

            socket.close();
        }

        //SNMP GET 요청 패킷 생성
        public byte[] makeGetRequestPacket(String oids) throws IOException{
            BEROutputStream bos = new BEROutputStream(ByteBuffer.allocate(256));

            byte requestByte = (byte)0xa0;
            oid = stringsToints(oids);

            int oidLen = BER.getOIDLength(oid);
            int varBindLen = oidLen + 2 + 2;
            int varBindListLen = varBindLen + 2;
            int pduLen = varBindListLen + 2 + 3 + 3 + 3;
            int messageLen = pduLen + 2 + 2 + community.length + 3;

            BER.encodeHeader(bos, BER.SEQUENCE, messageLen);
            BER.encodeInteger(bos, BER.INTEGER, 0x01);
            BER.encodeString(bos, BER.OCTETSTRING, community);
            BER.encodeHeader(bos, requestByte, pduLen);
            BER.encodeInteger(bos, BER.INTEGER, 0x03);
            BER.encodeInteger(bos, BER.INTEGER, 0x00);
            BER.encodeInteger(bos, BER.INTEGER, 0x00);
            BER.encodeHeader(bos, BER.SEQUENCE, varBindListLen);
            BER.encodeHeader(bos, BER.SEQUENCE, varBindLen);
            BER.encodeOID(bos, BER.OID, oid);
            BER.encodeHeader(bos, BER.NULL, 0x00);

            byte[] result = bos.getBuffer().array();

            bos.close();

            return result;
        }

        //SNMP SET 요청 패킷 생성
        public byte[] makeSetRequestPacket(String oids, String type, String value) throws IOException {
            BEROutputStream bos = new BEROutputStream(ByteBuffer.allocate(256));

            byte requestByte = (byte) 0xa3;
            oid = stringsToints(oids);

            int oidLen = BER.getOIDLength(oid);
            int varBindLen = oidLen + 2;

            if (type.equals("INTEGER")) {
                varBindLen += +2 + BER.getBERLengthOfLength(Integer.parseInt(value));
            }
            else if (type.equals("STRING")) {
                varBindLen += value.length() + 2;
            }
            else if (type.equals("OID")) {
                int[] temp = stringsToints(value);

                varBindLen += temp.length + 2;
            }
            else if(type.equals("Counter32")) {
                varBindLen += BER.getBERLengthOfLength(Integer.parseInt(value)) + 2;
            }
            else if(type.equals("Gauge32")) {
                varBindLen += BER.getBERLengthOfLength(Integer.parseInt(value)) + 2;
            }
            else if(type.equals("TimeTicks")) {
                varBindLen += BER.getBERLengthOfLength(Integer.parseInt(value)) + 2;
            }

            int varBindListLen = varBindLen + 2;
            int pduLen = varBindListLen + 2 + 3 + 3 + 3;
            int messageLen = pduLen + 2 + 2 + writeCoummunity.length + 3;

            BER.encodeHeader(bos, BER.SEQUENCE, messageLen);
            BER.encodeInteger(bos, BER.INTEGER, 0x01);
            BER.encodeString(bos, BER.OCTETSTRING, writeCoummunity);
            BER.encodeHeader(bos, requestByte, pduLen);
            BER.encodeInteger(bos, BER.INTEGER, 0x03);
            BER.encodeInteger(bos, BER.INTEGER, 0x00);
            BER.encodeInteger(bos, BER.INTEGER, 0x00);
            BER.encodeHeader(bos, BER.SEQUENCE, varBindListLen);
            BER.encodeHeader(bos, BER.SEQUENCE, varBindLen);
            BER.encodeOID(bos, BER.OID, oid);

            Log.d("TAG", type);

            if (type.equals("INTEGER")) {
                int temp = Integer.parseInt(value);

                BER.encodeInteger(bos, BER.INTEGER, temp);
            } else if (type.equals("STRING")) {
                byte[] temp = value.getBytes();

                BER.encodeString(bos, BER.OCTETSTRING, temp);
            } else if (type.equals("OID")) {
                int[] temp = stringsToints(value);

                BER.encodeOID(bos, BER.OID, temp);
            }
            else if(type.equals("Counter32")) {
                long temp = Long.parseLong(value);

                BER.encodeUnsignedInteger(bos, BER.COUNTER32, temp);
            }
            else if(type.equals("Gauge32")) {
                long temp = Long.parseLong(value);

                BER.encodeUnsignedInteger(bos, BER.GAUGE32, temp);
            }
            else if(type.equals("TimeTicks")) {
                long temp = Long.parseLong(value);

                BER.encodeUnsignedInteger(bos, BER.TIMETICKS, temp);
            }

            byte[] result = bos.getBuffer().array();

            bos.close();

            return result;
        }

        //SNMP GETNEXT 요청 패킷 생성
        public byte[] makeGetWalkRequestPacket(String oids) throws IOException{
            BEROutputStream bos = new BEROutputStream(ByteBuffer.allocate(512));

            byte requestByte = (byte)0xa1;
            oid = stringsToints(oids);

            int oidLen = BER.getOIDLength(oid);
            int varBindLen = oidLen + 2 + 2;
            int varBindListLen = varBindLen + 2;
            int pduLen = varBindListLen + 2 + 3 + 3 + 3;
            int messageLen = pduLen + 2 + 2 + community.length + 3;

            BER.encodeHeader(bos, BER.SEQUENCE, messageLen);
            BER.encodeInteger(bos, BER.INTEGER, 0x01);
            BER.encodeString(bos, BER.OCTETSTRING, community);
            BER.encodeHeader(bos, requestByte, pduLen);
            BER.encodeInteger(bos, BER.INTEGER, 0x03);
            BER.encodeInteger(bos, BER.INTEGER, 0x00);
            BER.encodeInteger(bos, BER.INTEGER, 0x00);
            BER.encodeHeader(bos, BER.SEQUENCE, varBindListLen);
            BER.encodeHeader(bos, BER.SEQUENCE, varBindLen);
            BER.encodeOID(bos, BER.OID, oid);
            BER.encodeHeader(bos, BER.NULL, 0x00);

            byte[] result = bos.getBuffer().array();

            bos.close();

            return result;
        }

        //SNMP 응답 패킷 분석
        public String[] decodeResponsePacket(byte[] packet) throws IOException{
            BER.MutableByte mutableByte = new BER.MutableByte();

            BERInputStream bis = new BERInputStream(ByteBuffer.wrap(packet));

            String str = "";
            String errorStr = "";
            String resultType = "";

            BER.decodeHeader(bis, mutableByte);
            BER.decodeInteger(bis, mutableByte);
            BER.decodeString(bis, mutableByte);
            BER.decodeHeader(bis, mutableByte);
            BER.decodeInteger(bis, mutableByte);

            int errorCode = BER.decodeInteger(bis, mutableByte);

            switch (errorCode) {
                case 0 : errorStr="noError";   break;
                case 1 : errorStr="tooBig";   break;
                case 2 : errorStr="noSuchName";   break;
                case 3 : errorStr="badValue";   break;
                case 4 : errorStr="readOnly";   break;
                case 5 : errorStr="genErr";   break;
                case 6 : errorStr="noAccess";   break;
                case 7 : errorStr="wrongType";   break;
                case 8 : errorStr="wrongLength";   break;
                case 9 : errorStr="wrongEncoding";   break;
                case 10 : errorStr="wrongValue";   break;
                case 11 : errorStr="noCreation";   break;
                case 12 : errorStr="inconsistentValue";   break;
                case 13 : errorStr="resourceUnavailable";   break;
                case 14 : errorStr="commitFailed";   break;
                case 15 : errorStr="undoFailed";   break;
                case 16 : errorStr="authorizationError";   break;
                case 17 : errorStr="notWritable";   break;
                case 18 : errorStr="inconsistentName";   break;
            }

            int errorIndex = BER.decodeInteger(bis, mutableByte);

            BER.decodeHeader(bis, mutableByte);
            BER.decodeHeader(bis, mutableByte);

            int[] oid = BER.decodeOID(bis, mutableByte);
            String nextOID = convertOID(oid);

            bis.mark(packet.length);

            BER.decodeHeader(bis, mutableByte);

            bis.reset();

            if(mutableByte.value == BER.INTEGER) {
                int temp = BER.decodeInteger(bis, mutableByte);

                resultType = "INTEGER";
                str = String.valueOf(temp);
            } else if(mutableByte.value == BER.OCTETSTRING) {
                byte[] temp = BER.decodeString(bis, mutableByte);

                if(isPrintable(temp)) {
                    resultType = "STRING";

                    str = "\"" + new String(temp) + "\"";
                }
                else {
                    resultType = "Hex-STRING";

                    for(int i=0;i<temp.length;++i) {
                        str += String.format("%02x", 0xff & temp[i]) + " ";
                    }
                }
            } else if(mutableByte.value == BER.NULL) {
                BER.decodeNull(bis, mutableByte);
            } else if(mutableByte.value == BER.OID) {
                int[] temp = BER.decodeOID(bis, mutableByte);

                resultType = "OID";
                for(int i=0;i<temp.length;++i) {
                    if(i != temp.length - 1)
                        str += String.valueOf(temp[i]) + ".";
                    else
                        str += String.valueOf(temp[i]);
                }
            } else if(mutableByte.value == BER.IPADDRESS) {
                byte[] temp = BER.decodeString(bis, mutableByte);

                resultType = "IPADDRESS";
                for(int i=0;i<temp.length;++i) {
                    if(i != temp.length - 1)
                        str += String.valueOf(temp[i]) + ".";
                    else
                        str += String.valueOf(temp[i]);
                }
            } else if(mutableByte.value == BER.COUNTER32) {
                long temp = BER.decodeUnsignedInteger(bis, mutableByte);

                resultType = "Counter32";
                str = String.valueOf(temp);
            } else if(mutableByte.value == BER.GAUGE32) {
                long temp = BER.decodeUnsignedInteger(bis, mutableByte);

                resultType = "Gauge32";
                str = String.valueOf(temp);
            } else if(mutableByte.value == BER.TIMETICKS) {
                long temp = BER.decodeUnsignedInteger(bis, mutableByte);

                resultType = "Timeticks";
                str = transDate(temp);
            } else if(mutableByte.value == BER.OPAQUE) {
                resultType = "Opaque";
                //Log.d("Receive", "Opaque");
            } else if(mutableByte.value == (byte)BER.NOSUCHOBJECT) {
                resultType = "noSuchObject";
                //Log.d("Receive", "noSuchObject");
            } else if(mutableByte.value == (byte)BER.NOSUCHINSTANCE) {
                resultType = "noSuchInstance";
                //Log.d("Receive", "noSuchInstance");
            } else if(mutableByte.value == (byte)BER.ENDOFMIBVIEW) {
                resultType = "endOfMibView";
            } else {
            }

            bis.close();

            String[] result = new String[4];

            if(errorIndex == 0) {
                result[0] = nextOID;
                result[1] = str;
                result[2] = resultType;
                result[3] = "0";

                return result;
            }
            else {
                result[0] = nextOID;
                result[1] = errorStr;
                result[2] = String.valueOf(errorCode);
                result[3] = "1";

                return result;
            }
        }

        //Timeticks 시간 변환
        public String transDate(long temp) {
            String msg = "({0}) {1} days, {2}:{3}:{4}.{5}";
            Object[] args = new Object[6];

            args[0] = temp;

            args[1] = temp / 8640000;

            temp %= 8640000;

            args[2] = temp / 360000;

            temp %= 360000;

            args[3] = temp / 6000;

            temp %= 6000;

            args[4] = temp / 100;

            args[5] = temp % 100;

            String result = MessageFormat.format(msg, args);

            return result;
        }

        //OID 값 변환
        public String convertOID(int[] temp) {
            String result = "";

            for(int i=0;i<temp.length;++i) {
                if(i != temp.length - 1)
                    result += String.valueOf(temp[i]) + ".";
                else
                    result += String.valueOf(temp[i]);
            }

            return result;
        }

        //결과 형식
        public String printResult(String[] temp) {
            String msg = "{0} = {2}: {1}";
            
            Object[] args = new Object[3];

            args[0] = temp[0];
            args[1] = temp[1];
            args[2] = temp[2];

            String result = MessageFormat.format(msg, args) + "\n";

            return result;
        }

        //에러 형식
        public String printErrorResult(String[] temp) {
            String msg = "{0} -> Error({2}) : {1}";

            Object[] args = new Object[3];

            args[0] = temp[0];
            args[1] = temp[1];
            args[2] = temp[2];

            String result = MessageFormat.format(msg, args) + "\n";

            return result;
        }

        //출력 가능 여부 확인
        public boolean isPrintable(byte[] value) {
            for (int i=0; i<value.length; i++) {
                char c = (char)value[i];
                if ((Character.isISOControl(c) || ((c & 0xFF) >= 0x80)) &&
                        ((!Character.isWhitespace(c)) ||
                                (((c & 0xFF) >= 0x1C)) && ((c & 0xFF) <= 0x1F))) {
                    return false;
                }
            }
            return true;
        }

        //OID 변환
        public int[] stringsToints(String s) {
            String[] oids = s.split("\\.");

            int[] arr = new int[oids.length];

            for(int i=0;i<oids.length;++i) {
                arr[i] = Integer.valueOf(oids[i]);
            }

            return arr;
        }
    }
}
