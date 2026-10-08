package com.example.habitquest.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.habitquest.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.model.Achievement;

import java.util.List;

/**
  Adapter osztály az Achievementek megjelenítéséhez RecyclerView-ban
  Feladata: a listaelemek (title, description, ikon) összekötése a modellel
 */
public class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.ViewHolder> {

    // Az adapter által kezelt achievement lista
    private List<Achievement> achievements;

    // Konstruktor: kívülről megkapja az Achievementek listáját
    public AchievementAdapter(List<Achievement> achievements) {
        this.achievements = achievements;
    }

    /**
     Új ViewHolder létrehozása
     Ezt hívja meg a RecyclerView, amikor új listaelemhez kell egy View
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Az item_achievement.xml fájl "felfújása" (layout létrehozása Java objektummá)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_achievement, parent, false); //false, hogy ne adja hozzá a parenthez
        return new ViewHolder(view);
    }

    /**
     Egy listaelem adatait összeköti a ViewHolder-ben lévő UI elemekkel.
     Itt történik az adatok megjelenítése.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Achievement achievement = achievements.get(position);

        // Cím és leírás szöveg kiírása
        holder.title.setText(achievement.getTitle());
        holder.description.setText(achievement.getDescription());

        // Ikon beállítása: unlocked vagy locked
        if (achievement.isUnlocked()) {
            holder.icon.setImageResource(R.drawable.achievement_unlock);
        } else {
            holder.icon.setImageResource(R.drawable.achievement_lock);
        }
    }

    /**
      Megmondja hány elem van a listában
      A RecyclerView ennek alapján rendereli a listaelemeket
     */
    @Override
    public int getItemCount() {
        return achievements.size();
    }

    /**
      ViewHolder belső osztály
      Ez tartalmazza az item_achievement.xml-ben lévő UI elemek referenciáit
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView icon;

        public ViewHolder(View itemView) {
            super(itemView);

            // Layout elemek összekötése az XML ID-k alapján
            title = itemView.findViewById(R.id.achievementTitle);
            description = itemView.findViewById(R.id.achievementDescription);
            icon = itemView.findViewById(R.id.achievementIcon);
        }
    }
}
