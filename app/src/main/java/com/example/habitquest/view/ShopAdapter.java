package com.example.habitquest.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.R;
import com.example.habitquest.model.ShopItem;

import java.util.List;

public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ShopViewHolder> {

    public interface OnBuyClickListener {
        void onBuy(ShopItem item);
    }

    private List<ShopItem> items;
    private final OnBuyClickListener listener;
    private int currentCoins = 0;

    public ShopAdapter(List<ShopItem> items, OnBuyClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<ShopItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setCurrentCoins(int currentCoins) {
        this.currentCoins = currentCoins;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ShopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shop, parent, false);
        return new ShopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShopViewHolder holder, int position) {
        ShopItem item = items.get(position);

        holder.emoji.setText(item.getEmoji());
        holder.name.setText(item.getName());
        holder.description.setText(item.getDescription());
        holder.buyButton.setText("💰 " + item.getPrice());
        holder.buyButton.setEnabled(currentCoins >= item.getPrice());
        holder.buyButton.setAlpha(currentCoins >= item.getPrice() ? 1f : 0.5f);

        holder.buyButton.setOnClickListener(v -> {
            if (listener != null) listener.onBuy(item);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ShopViewHolder extends RecyclerView.ViewHolder {
        TextView emoji, name, description;
        Button buyButton;

        ShopViewHolder(View itemView) {
            super(itemView);
            emoji = itemView.findViewById(R.id.shopItemEmoji);
            name = itemView.findViewById(R.id.shopItemName);
            description = itemView.findViewById(R.id.shopItemDescription);
            buyButton = itemView.findViewById(R.id.shopItemBuyButton);
        }
    }
}
