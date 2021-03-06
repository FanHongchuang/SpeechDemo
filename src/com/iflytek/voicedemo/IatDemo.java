package com.iflytek.voicedemo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;
import com.FT312D.utility.FT311UARTInterface;
import com.fan.util.RollCall;
import com.fan.util.ServerThread;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUnderstanderListener;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.TextUnderstander;
import com.iflytek.cloud.TextUnderstanderListener;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.util.JsonParser;
import com.iflytek.sunflower.FlowerCollector;
import com.iflytek.voicedemo.Player;

public class IatDemo extends Activity implements OnClickListener {
	private static String TAG = IatDemo.class.getSimpleName();

	// 用HashMap存储听写结果
	private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

	private EditText mResultText;
	private Toast mToast;
	private SharedPreferences mSharedPreferences;
	// 引擎类型
	private String mEngineType = SpeechConstant.TYPE_CLOUD;
	
	// 语义理解对象（语音到语义）。
	private SpeechUnderstander mSpeechUnderstander;
	
	// 语义理解对象（文本到语义）。
	private TextUnderstander   mTextUnderstander;
	
	// 语音听写对象
	private SpeechRecognizer mIat;
	
	//用于语音合成  add by Frank
	private SpeechSynthesizer mTts;

	// 默认发音人
	private String voicer = "xiaoai";
	// 情感
	private String emot= "";
	
	//用于存储命令词(此命令次为本项目自定义) add by Frank
	byte[] c = {0x01,0x02,0x03,0x04,0x15};
	
	//FT312D
	final int FORMAT_ASCII = 0;
	
	int inputFormat = FORMAT_ASCII;
	
	/* local variables */
	byte[] writeBuffer;
	byte[] readBuffer;
	char[] readBufferToChar;
	int[] actualNumBytes;

	int numBytes;
	byte count;
	byte status;
	byte writeIndex = 0;
	byte readIndex = 0;
	
	/* thread to read the data */
	public handler_thread handlerThread;

	/* declare a FT312D UART interface variable */
	public FT311UARTInterface uartInterface;
	
	private static boolean allowSpeechFalg = false;
	
	//音乐播放
	private Player player = null;

	private static int millisecond;
	
	private static int i = 1;
	
	private static String lastResultJsonString;
	

	
	

	
	private static final int PROCESSING = 1;
	private static final int FT312D = 2;
	
	private static String iatString;
	private static String understandString;
	private Boolean speechFlag = false;
	
	private StringBuffer resultBuffer;
	
	private boolean nextQuestionFlag;
	
	
	@SuppressLint("ShowToast")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.iatdemo);

		initLayout();
		
		// 初始化语音合成对象
		mTts = SpeechSynthesizer.createSynthesizer(IatDemo.this, mTtsInitListener);
		
		// 初始化识别无UI识别对象
		// 使用SpeechRecognizer对象，可根据回调消息自定义界面；
		mIat = SpeechRecognizer.createRecognizer(this, mInitListener);
		
		/**
		 * 申请的appid时，我们为开发者开通了开放语义（语义理解）
		 * 由于语义理解的场景繁多，需开发自己去开放语义平台：http://www.xfyun.cn/services/osp
		 * 配置相应的语音场景，才能使用语义理解，否则文本理解将不能使用，语义理解将返回听写结果。
		 */
		// 初始化语义理解对象（包含语音识别）
		mSpeechUnderstander = SpeechUnderstander.createUnderstander(IatDemo.this, mSpeechUdrInitListener);
		mTextUnderstander =  TextUnderstander.createTextUnderstander(this, textUnderstanderListener);

		mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME,
				Activity.MODE_PRIVATE);
		mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
		mResultText = ((EditText) findViewById(R.id.iat_text));
		
		// 设置语义理解参数
		setUnderstanderParam();
		
		// 设置语音合成参数
		setTtsParam();
		
		/*
		 * 设置语音唤醒参数（试用期已过）
		 * 代码已经写好，购买唤醒词之后，将对应的唤醒资源Copy到本工程后，即可使用唤醒功能
		 * 
		 */
//		setWeakupParam();
		
		player = new Player();
		
//		if(player == null){
//			player = new Player();
//		}
//		new Thread(new Runnable() {
//
//			@Override
//			public void run() {
//				player.playUrl("http://192.168.1.102:8080/music/开机问候语.mp3");
//			}
//		}).start();
		
//        new ServerThread().start();
		
		
	}

	/**
	 * 初始化Layout。
	 */
	private void initLayout() {
		findViewById(R.id.iat_recognize).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_upload_contacts).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_upload_userwords).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_stop).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_cancel).setOnClickListener(IatDemo.this);
		findViewById(R.id.image_iat_set).setOnClickListener(IatDemo.this);
		findViewById(R.id.write).setOnClickListener(IatDemo.this);
		findViewById(R.id.start).setOnClickListener(IatDemo.this);
		findViewById(R.id.pause).setOnClickListener(IatDemo.this);
		findViewById(R.id.resume).setOnClickListener(IatDemo.this);
		
		
		mEngineType = SpeechConstant.TYPE_CLOUD;
		
		
		/* allocate buffer */
		writeBuffer = new byte[64];
		readBuffer = new byte[4096];
		readBufferToChar = new char[4096]; 
		actualNumBytes = new int[1];
		
		uartInterface = new FT311UARTInterface(IatDemo.this, null);

		handlerThread = new handler_thread(handler);
		
		handlerThread.start();
		
	}

	int ret = 0; // 函数调用返回值

	

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		// 进入参数设置页面
		case R.id.image_iat_set:
			Intent intents = new Intent(IatDemo.this, IatSettings.class);
			startActivity(intents);
			break;
		// 开始听写
		// 如何判断一次听写结束：OnResult isLast=true 或者 onError
		case R.id.iat_recognize:
			mResultText.setText(null);// 清空显示内容
			mIatResults.clear();
			
			
			
			if(mSpeechUnderstander.isUnderstanding()){// 开始前检查状态
				mSpeechUnderstander.stopUnderstanding();
				showTip("停止录音");
			}else {
				ret = mSpeechUnderstander.startUnderstanding(mSpeechUnderstanderListener);
				if(ret != 0){
					showTip("语义理解失败,错误码:"	+ ret);
				}else {
					showTip(getString(R.string.text_begin));
				}
			}
			break;
		// 停止听写
		case R.id.iat_stop:
			showTip("停止听写");
			break;
		// 取消听写
		case R.id.iat_cancel:
			showTip("取消听写");
			player.pause();
			break;
			
		case R.id.start:
			showTip("开始播放");
			mIat.startListening(mRecognizerListener);
			break;
			
		case R.id.pause:

			showTip("暂停播放");
			millisecond = player.getCurrentPosition();
			player.pause();
			break;
			
		case R.id.resume:

			showTip("继续播放");
			player.seekTo(millisecond);
			player.play();
			break;
					
		case R.id.write:
//			writeData("123");  //以字符串的形式发送。"123"就是 49 50 51
			uartInterface.SendOneByte((byte) 1); //以字节的形式发送
			break;
		default:
			break;
		}
	}

	/**
	 * 初始化语音识别监听器。
	 */
	private InitListener mInitListener = new InitListener() {

		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败，错误码：" + code);
			}
		}
	};
	
	
	
	/**
	 * 初始化语音合成监听。
	 */
	private InitListener mTtsInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "InitListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
        		showTip("初始化失败,错误码："+code);
        	} else {
				// 初始化成功，之后可以调用startSpeaking方法
        		// 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
        		// 正确的做法是将onCreate中的startSpeaking调用移至这里
			}		
		}
	};
	
	
	
	
	/**
     * 初始化语义理解监听器（语音到语义）。
     */
    private InitListener mSpeechUdrInitListener = new InitListener() {
    	
		@Override
		public void onInit(int code) {
			Log.d(TAG, "speechUnderstanderListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
        		showTip("初始化失败,错误码："+code);
        	}			
		}
    };
    
    
    /**
     * 初始化监听器（文本到语义）。
     */
    private InitListener textUnderstanderListener = new InitListener() {

		@Override
		public void onInit(int code) {
			Log.d(TAG, "textUnderstanderListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
        		showTip("初始化失败,错误码："+code);
        	}	
		}
    };
    
    
    /**
     * 语义理解回调。
     */
    private SpeechUnderstanderListener mSpeechUnderstanderListener = new SpeechUnderstanderListener() {

		

    	private String urlString = null;
    	private String text = null;
		
    	private JSONObject root = null;    //获取Json整体对象
    	private String service = null;     //获取service字段
    	private JSONObject answer = null;  //获取answer字段
    	private String answerText = null;  //获取answer字段中的text字段

		@SuppressLint("NewApi")
		@Override
		public void onResult(final UnderstanderResult UdrResult) {
			
			
			
			
			
			/*
			 * 语义分析使用完MIC后，重新启动语音唤醒
			 * 语义分析过程中，可能会出现错误，所以在onError()方法中也添加此段代码
			 * 
			 */
//			mIvw = VoiceWakeuper.getWakeuper();
//			if (mIvw != null) {
//				mIvw.startListening(mWakeuperListener);
//			} else {
//				showTip("唤醒未初始化");
//			}
			
			/*
			 * 解析Json字符串
			 * 
			 */
			if (null != UdrResult) {
				Log.d(TAG, UdrResult.getResultString());
				
				// 显示
				final String jsonString = UdrResult.getResultString();
				
				mResultText.setText(jsonString);
				
				try {
					
					root = new JSONObject(jsonString);
					int rc = root.getInt("rc");
					
				switch (rc) {
					case 0:
						
						service  = root.getString("service");

						mResultText.setText(service);
						
						break;
						
					case 1:
						showTip("无效请求");
						break;
						
					case 2:
						showTip("服务器内部错误");
						break;
						
					case 3:
						showTip("业务操作失败");
						break;
						
					case 4:
						showTip("文本没有匹配的服务场景");
						
						break;

					
					}
					

				
				switch (ServiceName.getServiceName(service)) {
					
					case openQA://通用问答
						
						answer = root.getJSONObject("answer");  //answer Json对象，经语义理解后的回话
						answerText = answer.getString("text");  //text字段，经语义理解后的回话
						text = root.getString("text");          //text字段，原话
						if (text.contains("考考")) {
							mResultText.setText("考考");
							if(mTextUnderstander.isUnderstanding()){
								mTextUnderstander.cancel();
								showTip("取消");
							}else {
								ret = mTextUnderstander.understandText(answerText, textListener);
								if(ret != 0)
								{
									showTip("语义理解失败,错误码:"+ ret);
								}
							}
							
//							mTts.startSpeaking(answerText, mTtsListener);
							speechFlag = true;
						}
						
						
						
//						mResultText.setText(answerText);
						
						
						mTts.startSpeaking(answerText, mTtsListener);
						
						
						break;
						
					case calc://计算

						answer = root.getJSONObject("answer");
						answerText = answer.getString("text");
						mTts.startSpeaking(answerText, mTtsListener);
						break;
						
					case music://音乐
						JSONObject data = root.getJSONObject("data");
					    JSONArray result = data.getJSONArray("result");
						JSONObject url = result.getJSONObject(0);
						urlString = url.getString("downloadUrl");
						mResultText.setText(urlString);
						if (!TextUtils.isEmpty(urlString)) {
							lastResultJsonString = jsonString;
							i = 1;
							new Thread(new Runnable() {
	//
								@Override
								public void run() {
										
									player.playUrl(urlString);
									
								}
							}).start();
						}
						break;
						
					case weather://天气
						
						JSONObject semantic = root.getJSONObject("semantic");  //semantic Json对象，语义理解后返回
						
						JSONObject slots = semantic.getJSONObject("slots");
						//时间
						JSONObject datetime = slots.getJSONObject("datetime");  //datetime Json对象，语义理解后返回
						String date = datetime.getString("date");              //date字段，包含时间信息
						
						//地点
						JSONObject location = slots.getJSONObject("location");  //location Json对象，语义理解后返回
						String city = location.getString("city");              //city字段，包含地点信息
						
						if("CURRENT_CITY".equals(city)){
							
							mTts.startSpeaking("小朋友，告诉我你在哪个城市？", mTtsListener);
							speechFlag = true;
							isWeather = true;
						
						}else{
							
							//获取天气信息
							JSONObject weatherData = root.getJSONObject("data");
							JSONArray weatherResult = weatherData.getJSONArray("result");
							JSONObject weatherJsonObject = weatherResult.getJSONObject(1);
							//weather
							String weather = weatherJsonObject.getString("weather");
							//wind
							String wind = weatherJsonObject.getString("wind");
							//tempRange
							String tempRange = weatherJsonObject.getString("tempRange");
							
							//播报天气
							mTts.startSpeaking(city+"今天"+weather+","+"风力："+wind+","+"温度"+tempRange, mTtsListener);
						}
						
						break;
						
					case baike://百科
						answer = root.getJSONObject("answer");
						answerText = answer.getString("text");
						mTts.startSpeaking(answerText, mTtsListener);
						
						break;
						
					case datetime://日期
						answer = root.getJSONObject("answer");
						answerText = answer.getString("text");
						mTts.startSpeaking(answerText, mTtsListener);
						break;
						
					case faq://社区问答
						answer = root.getJSONObject("answer");
						answerText = answer.getString("text");
						mTts.startSpeaking(answerText, mTtsListener);
						break;
						
					case chat://聊天
						answer = root.getJSONObject("answer");
						answerText = answer.getString("text");
						mTts.startSpeaking(answerText, mTtsListener);
						break;
						
						
					case translation://聊天
						
						mTts.startSpeaking(answerText, mTtsListener);
						break;

					default:
						mTts.startSpeaking("我不理解你的意思，你再说一遍好吗？", mTtsListener);
						break;
					}
					
					

					
					
							

					
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				

//				if (!TextUtils.isEmpty(text) && "暂停播放。".equals(text)) {
//					millisecond = player.getCurrentPosition();
//					player.pause();
//					mResultText.setText(text);		
//				}else if (!TextUtils.isEmpty(text) && "继续播放。".equals(text)) {
//					player.seekTo(millisecond);
//					player.play();
//				}else if ("换一首。".equals(text)) {
//					
//					mResultText.setText(text);
//					try {
//						JSONObject root1 = new JSONObject(lastResultJsonString);
//						
//						JSONObject data1 = root1.getJSONObject("data");
//						JSONArray result1 = data1.getJSONArray("result");
//						JSONObject url1 = result1.getJSONObject(i++);
//						urlString = url1.getString("downloadUrl");
//						mResultText.setText(urlString);
//						
//
//							
//					} catch (JSONException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//					
//					if (!TextUtils.isEmpty(urlString)) {
//						
//						new Thread(new Runnable() {
//
//							@Override
//							public void run() {
//									
//								player.playUrl(urlString);
//								
//							}
//						}).start();
//					}
//				}
			} else {
				showTip("识别结果不正确。");
			}	
		}
    	
        @Override
        public void onVolumeChanged(int volume, byte[] data) {
        	showTip("当前正在说话，音量大小：" + volume);
        	Log.d(TAG, data.length+"");
        }
        

		@Override
        public void onEndOfSpeech() {
        	// 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
        	showTip("结束说话");	
        }
        
        @Override
        public void onBeginOfSpeech() {
        	// 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
        	showTip("开始说话");
        }

		@Override
		public void onError(SpeechError error) {
			
			/*
			 * 语义分析使用完MIC后，重新启动语音唤醒
			 * 
			 */
//			mIvw = VoiceWakeuper.getWakeuper();
//			if (mIvw != null) {
//				mIvw.startListening(mWakeuperListener);
//			} else {
//				showTip("唤醒未初始化");
//			}
			
			showTip(error.getPlainDescription(true));
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
    };

    /*
     * 文本理解回调
     */
    private TextUnderstanderListener textListener = new TextUnderstanderListener() {
		
		@Override
		public void onResult(final UnderstanderResult result) {
			
	       	runOnUiThread(new Runnable() {
					private String textString;
					private JSONObject answer;

					@Override
					public void run() {
						if (null != result) {
			            	// 显示
							Log.d(TAG, "understander result：" + result.getResultString());
							
							final String text = result.getResultString();
									
							try {
							JSONObject root = new JSONObject(text);
							String service = root.getString("service");
							mResultText.append(service);
							if ("openQA".equals(service)) {
								answer = root.getJSONObject("answer");
								textString = answer.getString("text");	
								mResultText.append(textString);
								//向服务器请求题目答案
								if(mTextUnderstander.isUnderstanding()){
									mTextUnderstander.cancel();
									showTip("取消");
								}else {
									ret = mTextUnderstander.understandText(textString, textListener);
									if(ret != 0)
									{
										showTip("语义理解失败,错误码:"+ ret);
									}
								}
								
								setTtsParam();
								mTts.startSpeaking(textString, mTtsListener);
								speechFlag = true;
								
							}else if("calc".equals(service)){ 
								answer = root.getJSONObject("answer");
								textString = answer.getString("text");
								//从字符串( eg:等于2 )中抽取答案
								String regEx="[^0-9]";   
								Pattern p = Pattern.compile(regEx);   
								Matcher m = p.matcher(textString);   
								understandString = m.replaceAll("").trim();
								
								/*
								 *   不能在此处，从用户语音中获取答案，因为程序运行到这里的时候，语音合成还未结束
								 *   所以将 “ 从用户语音中获取答案  ”，的动作放到语音合成结束的回调函数中。
								 *   
								 */
								
							}else if ("weather".equals(service)) {
//										
								//获取城市信息
								JSONObject semantic = root.getJSONObject("semantic");  //semantic Json对象，语义理解后返回
								JSONObject slots = semantic.getJSONObject("slots");
								//地点
								JSONObject location = slots.getJSONObject("location");  //location Json对象，语义理解后返回
								String city = location.getString("city");              //city字段，包含地点信息
								//获取天气信息
								JSONObject data = root.getJSONObject("data");
								JSONArray result = data.getJSONArray("result");
								JSONObject weatherJsonObject = result.getJSONObject(1);
								//weather
								String weather = weatherJsonObject.getString("weather");
								//wind
								String wind = weatherJsonObject.getString("wind");
								//tempRange
								String tempRange = weatherJsonObject.getString("tempRange");
								
								//播报天气
								mTts.startSpeaking(city+"今天"+weather+","+"风力："+wind+","+"温度"+tempRange, mTtsListener);
								
								
								
							}
							
							
							
							
//									understandString = textString.substring(2);
//									mResultText.append(understandString);
//									//定位光标到句尾
//									mResultText.setSelection(mResultText.length());
						
								
								
						
								
								
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
			            } else {
			                Log.d(TAG, "understander result:null");
			                showTip("识别结果不正确。");
			            }
					}
				});
		}
		
		@Override
		public void onError(SpeechError error) {
			// 文本语义不能使用回调错误码14002，请确认您下载sdk时是否勾选语义场景和私有语义的发布
			showTip("onError Code："	+ error.getErrorCode());
			
		}
	};

	protected boolean isWeather = false;

	
    
    
    /**
	 * 听写监听器。
	 */
	private RecognizerListener mRecognizerListener = new RecognizerListener() {

		@Override
		public void onBeginOfSpeech() {
			// 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
			showTip("开始说话");
		}

		@Override
		public void onError(SpeechError error) {
			// Tips：
			// 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
			if (error.getPlainDescription(true).contains("10118")) {
				showTip("您没有说话");
			}
			
		}

		@Override
		public void onEndOfSpeech() {
			// 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
			showTip("结束说话");
			mIat.stopListening();
		}

		@Override
		public void onResult(RecognizerResult results, boolean isLast) {		
			String text = JsonParser.parseIatResult(results.getResultString());
			String sn = null;
			
			// 读取json结果中的sn字段
			try {
				JSONObject resultJson = new JSONObject(results.getResultString());
				sn = resultJson.optString("sn");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			mIatResults.put(sn, text);

			
			resultBuffer = new StringBuffer();
			for (String key : mIatResults.keySet()) {
				resultBuffer.append(mIatResults.get(key));
			}
			
			if(isLast) {
				
				

				String resultString = resultBuffer.toString();
						
				mResultText.append(resultString+"\n");
//				
				mResultText.append(understandString+"\n");
				
				String regEx="[^0-9]";   
				Pattern p = Pattern.compile(regEx);   
				Matcher m = p.matcher(resultString);   
				iatString = m.replaceAll("").trim();
				
				mResultText.append(iatString+"\n");
				
				if (resultString.contains("点名")) {
					
//					System.out.println("进入点名时间");
//					mTts.startSpeaking("好的，现在进入点名时间", mTtsListener);
					
					new RollCall().start();
				}
				
				
				if (isWeather) {
					String local = resultString.substring(0,(resultString.length()-1));
					isWeather = false;
					mResultText.append(local+"\n");
					if(mTextUnderstander.isUnderstanding()){
						mTextUnderstander.cancel();
						showTip("取消");
					}else {
						ret = mTextUnderstander.understandText(local+"今天天气怎么样？", textListener);
						
						if(ret != 0)
						{
							showTip("语义理解失败,错误码:"+ ret);
						}
					}
					
				}
				
				if ((iatString != null) && (understandString != null)) {
					if (iatString.equals(understandString)) {
						
						speechFlag = false;
						uartInterface.SendOneByte((byte) 1); //以字节的形式发送数据到控制板
						//恭喜你答对了.mp3
						mTts.startSpeaking("答对了！小主人，你真棒！我再考考你吧！", mTtsListener);
									
					}else {
						uartInterface.SendOneByte((byte) 2); //以字节的形式发送数据到控制板
						mResultText.append(understandString+"\n");
						mResultText.append(iatString+"\n");
						nextQuestionFlag = false;
						speechFlag = false;
						mTts.startSpeaking("答错了！应该等于"+understandString, mTtsListener);
					}
				}
			}
		}

		@Override
		public void onVolumeChanged(int volume, byte[] data) {
			showTip("当前正在说话，音量大小：" + volume);
			Log.d(TAG, "返回音频数据："+data.length);
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};
    
    /*
     * 语音唤醒回调
     * 
     * 
     */
//    private WakeuperListener mWakeuperListener = new WakeuperListener() {
//
//		@Override
//		public void onResult(WakeuperResult result) {
//			try {
//				String text = result.getResultString();
//				JSONObject object;
//				object = new JSONObject(text);
//				StringBuffer buffer = new StringBuffer();
//				buffer.append("【RAW】 "+text);
//				buffer.append("\n");
//				buffer.append("【操作类型】"+ object.optString("sst"));
//				buffer.append("\n");
//				buffer.append("【唤醒词id】"+ object.optString("id"));
//				buffer.append("\n");
//				buffer.append("【得分】" + object.optString("score"));
//				buffer.append("\n");
//				buffer.append("【前端点】" + object.optString("bos"));
//				buffer.append("\n");
//				buffer.append("【尾端点】" + object.optString("eos"));
//				resultString =buffer.toString();
//			} catch (JSONException e) {
//				resultString = "结果解析出错";
//				e.printStackTrace();
//			}
//			mResultText.setText(resultString);
//			
//			mResultText.setText(null);// 清空显示内容
//			mIatResults.clear();
//			
//			/*
//			 * 停止语音唤醒，让出MIC使用权，交由语音识别使用
//			 * 
//			 */
//			mIvw = VoiceWakeuper.getWakeuper();
//			if (mIvw != null) {
//				mIvw.stopListening();
//			} else {
//				showTip("唤醒未初始化");
//			}
//			
//			
//			/*
//			 * 唤醒后进行语义分析
//			 * 
//			 */
//			if(mSpeechUnderstander.isUnderstanding()){// 开始前检查状态
//				mSpeechUnderstander.stopUnderstanding();
//				showTip("停止录音");
//			}else {
//				ret = mSpeechUnderstander.startUnderstanding(mSpeechUnderstanderListener);
//				if(ret != 0){
//					showTip("语义理解失败,错误码:"	+ ret);
//				}else {
//					showTip(getString(R.string.text_begin));
//				}
//			}
//			
//			
//		}
//
//		@Override
//		public void onError(SpeechError error) {
//			showTip(error.getPlainDescription(true));
//		}
//
//		@Override
//		public void onBeginOfSpeech() {
//			showTip("开始说话");
//		}
//
//		@Override
//		public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
//
//		}
//
//		@Override
//		public void onVolumeChanged(int volume) {
//			// TODO Auto-generated method stub
//			
//		}
//	};
	

	/**
	 * 合成回调监听。
	 */
	private SynthesizerListener mTtsListener = new SynthesizerListener() {
		
		

		@Override
		public void onSpeakBegin() {
			showTip("开始播放");
		}

		@Override
		public void onSpeakPaused() {
			showTip("暂停播放");
		}

		@Override
		public void onSpeakResumed() {
			showTip("继续播放");
		}

		@Override
		public void onBufferProgress(int percent, int beginPos, int endPos,
				String info) {
			
		}

		@Override
		public void onSpeakProgress(int percent, int beginPos, int endPos) {
			// 播放进度
	
			
		}

		@Override
		public void onCompleted(SpeechError error) {
			if (error == null) {
				showTip("播放完成");
				if (speechFlag) {
					
					speechFlag = false;
					
					//停止语义理解，让出MIC
					mSpeechUnderstander.stopUnderstanding();
					
					// 开始语音识
					ret = mIat.startListening(mRecognizerListener);
					if (ret != ErrorCode.SUCCESS) {
						showTip("听写失败,错误码：" + ret);
					} else {
						showTip(getString(R.string.text_begin));
					}
				}
				
				
				//再次出题
				if (nextQuestionFlag) {
					
					nextQuestionFlag = false;
					//向服务器请求题目
					if(mTextUnderstander.isUnderstanding()){
						mTextUnderstander.cancel();
						showTip("取消");
					}else {
						ret = mTextUnderstander.understandText("机器人，考考我吧？", textListener);
						if(ret != 0)
						{
							showTip("语义理解失败,错误码:"+ ret);
						}
					}
				}
				
				
			} else if (error != null) {
				showTip(error.getPlainDescription(true));
			}
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};
	

	private void showTip(final String str) {
		mToast.setText(str);
		mToast.show();
	}
	
	/*
	 * 语音唤醒参数设置
	 * 
	 */
//	private void setWeakupParam(){
//		
//		// 加载识唤醒地资源，resPath为本地识别资源路径
//		StringBuffer param = new StringBuffer();
//		String resPath = ResourceUtil.generateResourcePath(IatDemo.this,
//				RESOURCE_TYPE.assets, "ivw/" + getString(R.string.app_id) + ".jet");
//		
//		param.append(SpeechConstant.IVW_RES_PATH + "=" + resPath);
//		param.append("," + ResourceUtil.ENGINE_START + "=" + SpeechConstant.ENG_IVW);
//		boolean ret = SpeechUtility.getUtility().setParameter(
//				ResourceUtil.ENGINE_START, param.toString());
//		if (!ret) {
//			Log.d(TAG, "启动本地引擎失败！");
//		}
//		// 初始化唤醒对象
//		mIvw = VoiceWakeuper.createWakeuper(this, null);
//		
//		//非空判断，防止因空指针使程序崩溃
//		mIvw = VoiceWakeuper.getWakeuper();
//		if(mIvw != null) {
//			resultString = "";
//			mResultText.setText(resultString);
//			// 清空参数
//			mIvw.setParameter(SpeechConstant.PARAMS, null);
//			/**
//			 * 唤醒门限值，根据资源携带的唤醒词个数按照“id:门限;id:门限”的格式传入
//			 * 示例demo默认设置第一个唤醒词，建议开发者根据定制资源中唤醒词个数进行设置
//			 */
//			mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:"
//					+ curThresh);
//			// 设置唤醒模式
//			mIvw.setParameter(SpeechConstant.IVW_SST, "wakeup");
//			// 设置持续进行唤醒
//			mIvw.setParameter(SpeechConstant.KEEP_ALIVE, "1");
//			mIvw.startListening(mWakeuperListener);
//		} else {
//			showTip("唤醒未初始化");
//		}
//	}
	
	
	/**
	 * 语音合成参数设置
	 * @param param
	 * @return 
	 */
	private void setTtsParam(){
		// 清空参数
		mTts.setParameter(SpeechConstant.PARAMS, null);
		// 根据合成引擎设置相应参数
		if(mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
			mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
			// 设置在线合成发音人
			mTts.setParameter(SpeechConstant.VOICE_NAME, voicer);
			if(!"neutral".equals(emot)){
				// 当前仅发音人“小艾”支持设置情感
				// “小艾”发音人需要付费使用，具体请联系：msp_support@iflytek.com
				mTts.setParameter(SpeechConstant.EMOT, emot);
			}
			//设置合成语速
			mTts.setParameter(SpeechConstant.SPEED, mSharedPreferences.getString("speed_preference", "60"));
			//设置合成音调
			mTts.setParameter(SpeechConstant.PITCH, mSharedPreferences.getString("pitch_preference", "50"));
			//设置合成音量
			mTts.setParameter(SpeechConstant.VOLUME, mSharedPreferences.getString("volume_preference", "50"));
		}else {
			mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
			// 设置本地合成发音人 voicer为空，默认通过语记界面指定发音人。
			mTts.setParameter(SpeechConstant.VOICE_NAME, "");
			/**
			 * TODO 本地合成不设置语速、音调、音量，默认使用语记设置
			 * 开发者如需自定义参数，请参考在线合成参数设置
			 */
		}
		//设置播放器音频流类型
		mTts.setParameter(SpeechConstant.STREAM_TYPE, mSharedPreferences.getString("stream_preference", "3"));
		// 设置播放合成音频打断音乐播放，默认为true
		mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");
		
		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
		mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/tts.wav");
	}
	

	/**
	 * 语义理解参数设置(包括语音识别)
	 * 
	 * @param param
	 * @return
	 */
	public void setUnderstanderParam() {
		// 清空参数
		mSpeechUnderstander.setParameter(SpeechConstant.PARAMS, null);

		// 设置听写引擎
		mSpeechUnderstander.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
		// 设置返回结果格式
		mSpeechUnderstander.setParameter(SpeechConstant.RESULT_TYPE, "json");

		String lag = mSharedPreferences.getString("iat_language_preference",
				"mandarin");
		if (lag.equals("en_us")) {
			// 设置语言
			mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "en_us");
		} else {
			// 设置语言
			mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
			// 设置语言区域
			mSpeechUnderstander.setParameter(SpeechConstant.ACCENT, lag);
		}

		// 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
		mSpeechUnderstander.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));
		
		// 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
		mSpeechUnderstander.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));
		
		// 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
		mSpeechUnderstander.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));
		
		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mSpeechUnderstander.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
		mSpeechUnderstander.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");
		
		// 设置听写结果是否结果动态修正，为“1”则在听写过程中动态递增地返回结果，否则只在听写结束之后返回最终结果
		// 注：该参数暂时只对在线听写有效
		mSpeechUnderstander.setParameter(SpeechConstant.ASR_DWA, mSharedPreferences.getString("iat_dwa_preference", "0"));

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// 退出时释放连接
//		mIat.cancel();
//		mIat.destroy();
		// 退出时释放连接
    	mSpeechUnderstander.cancel();
    	mSpeechUnderstander.destroy();
    	
    	
		mTts.stopSpeaking();
		// 退出时释放连接
		mTts.destroy();
		
		//FT312D
		uartInterface.DestroyAccessory(true);
		
		//音乐播放器
		if (player != null) {
			player.stop();
			player = null;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onResume() {
		// 开放统计 移动数据统计分析
		FlowerCollector.onResume(IatDemo.this);
		FlowerCollector.onPageStart(TAG);
		super.onResume();
		//FT312D
		uartInterface.ResumeAccessory();
		uartInterface.SetConfig(9600,(byte)8,(byte)1,(byte)0,(byte)0);
	}

	@Override
	protected void onPause() {
		// 开放统计 移动数据统计分析
		FlowerCollector.onPageEnd(TAG);
		FlowerCollector.onPause(IatDemo.this);
		super.onPause();
	}

	
	Thread thread = new Thread(new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
		}
	});
	
	
	
	
	/*
	 * 
	 * **********************************************************************************************************************
	 * 
	 *                                以下为FT312D操作
	 * 
	 * **********************************************************************************************************************
	 * 
	 */
	
	 /*
     * 以十进制写入数据
     * Modified by frank、
     * 
     */
    public void writeData(String string)
    {
    	String srcStr = string;    	
    	String destStr = "";
    	
    	destStr = srcStr;

		numBytes = destStr.length();
		for (int i = 0; i < numBytes; i++) {
			writeBuffer[i] = (byte)destStr.charAt(i);
		}
		uartInterface.SendData(numBytes, writeBuffer);
		
    }
	
	
	
	//@Override
		public void onHomePressed() {
			onBackPressed();
		}	

		public void onBackPressed() {
		    super.onBackPressed();
		}
	
	/*
	 * 
	 * 接收USB传输过来的数据，更新UI
	 * 
	 */
	@SuppressLint("HandlerLeak") 
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			switch (msg.what) {
				
			case FT312D:
				
				for(int i=0; i<actualNumBytes[0]; i++)
				{
					readBufferToChar[i] = (char)readBuffer[i];
				}	
				
				String s1 = new String(readBufferToChar).trim();
				
				if("ok".equals(s1)){
				 	allowSpeechFalg = true;
				 	showTip(s1+"true");
				 	
				 	/*
				 	 * 当触发传感器时，开始语音识别
				 	 * 在这里添加语音识别代码
				 	 */
				 	//

				}else {
					allowSpeechFalg = false;
					showTip(s1+"false");
				}
				
				break;

			default:
				break;
			}
			
			
			
		}
	};
	
	
	/*
	 * 
	 * 接收USB数据线程
	 * 
	 * 
	 */
	private class handler_thread extends Thread {
		Handler mHandler;

		/* constructor */
		handler_thread(Handler h) {
			mHandler = h;
		}

		public void run() {
			Message msg;

			while (true) {
	
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
				}

				status = uartInterface.ReadData(4096, readBuffer,actualNumBytes);

				if (status == 0x00 && actualNumBytes[0] > 0) {
					msg = mHandler.obtainMessage();
					msg.what = FT312D;
					mHandler.sendMessage(msg);
					
				}

			}
		}
	}
		

}
