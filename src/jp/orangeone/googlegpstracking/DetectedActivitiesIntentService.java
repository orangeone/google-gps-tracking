package jp.orangeone.googlegpstracking;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognitionResult;

public class DetectedActivitiesIntentService extends IntentService {

	public DetectedActivitiesIntentService() {
		super("ReceiveRecognitionIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (!ActivityRecognitionResult.hasResult(intent)) {
			// 行動認識結果持ってないよ
			return;
		}

		// 認識結果を取得する
		ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

		GPSHelper.setState(result);
	}
}
