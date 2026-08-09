package com.example.notificationblocker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TimeRangeAdapter extends RecyclerView.Adapter<TimeRangeAdapter.ViewHolder> {

    private final List<TimeRange> ranges;
    private final OnTimeRangeClickListener listener;

    public interface OnTimeRangeClickListener {
        void onEditClick(int position);
        void onDeleteClick(int position);
    }

    public TimeRangeAdapter(List<TimeRange> ranges, OnTimeRangeClickListener listener) {
        this.ranges = ranges;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_time_range, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeRange range = ranges.get(position);
        holder.timeText.setText(range.format());
        holder.itemView.setOnClickListener(v -> listener.onEditClick(holder.getAdapterPosition()));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return ranges.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView timeText;
        ImageView btnDelete;

        ViewHolder(View v) {
            super(v);
            timeText = v.findViewById(R.id.time_text);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
