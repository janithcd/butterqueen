package lk.jiat.butterqueen.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.activity.NotificationInboxActivity;

public final class OrderNotificationHelper {
    private static final String CHANNEL_ID = "butterqueen_orders";
    private static final int NOTIFICATION_ID = 501;

    private OrderNotificationHelper() {}

    public static void showOrderPlaced(Context context, String orderId, boolean pendingPayment) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Order Updates", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications about ButterQueen orders");
            manager.createNotificationChannel(channel);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, NotificationInboxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, orderId == null ? 0 : orderId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = pendingPayment
                ? "Your PayHere sandbox order is recorded. Tap to view activity."
                : "Your order has been placed! Tap to see it in Notifications.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ButterQueen - Order Update")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context).notify(
                orderId == null ? NOTIFICATION_ID : orderId.hashCode(), builder.build());
    }
}
