package com.topcoder.shared.util.dwload;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TCLoadBackPopulate extends TCLoad {

	public void performLoad() throws Exception {
		loadRankHistoryActive();
	}

	public boolean setParameters(Hashtable params) {
		return true;
	}
	
	private void loadRankHistoryActive() {
		try {
			System.out.println("0");
			
			Map coders = new HashMap();
			
			String GET_ROUNDS = "select round.round_id, round.calendar_id, calendar.date from round, calendar where round.calendar_id = calendar.calendar_id order by round.calendar_id;";
			String GET_CODERS = "select coder_id, new_rating from room_result where round_id = ? and rated_flag = 1;";
			String UPDATE_RANK_HISTORY = "insert into coder_rank_history (coder_id, round_id, percentile, rank, coder_rank_type_id) values (?, ?, ?, ?, 2);";
			
			PreparedStatement getRounds = prepareStatement(GET_ROUNDS, TARGET_DB);
			PreparedStatement getCoders = prepareStatement(GET_CODERS, TARGET_DB);
			PreparedStatement updateRankHistory = prepareStatement(UPDATE_RANK_HISTORY, TARGET_DB);
			
			System.out.println("1");
			
			ResultSet rs = getRounds.executeQuery();
			
			System.out.println("2");
			
			// each valid round
			while(rs.next()) {
				int roundId = rs.getInt("round_id");
				Date date = rs.getDate("date");
				long time = date.getTime();
				
				System.out.println("Round: " + roundId);
				
				// each coder rated in the round
				getCoders.setInt(1, roundId);
				ResultSet rs2 = getCoders.executeQuery();
				while(rs2.next()) {
					long coderId = rs2.getLong("coder_id");
					int rating = rs2.getInt("new_rating");
					Long key = new Long(coderId);
					Coder coder = (Coder)coders.get(key);
					if (coder == null) {
						coder = new Coder();
						coder.coderId = coderId;
						coders.put(key, coder);
					}
					coder.rating = rating;
					coder.lastRatedTime = time;
				}
				rs2.close();
				
				System.out.println(coders.size());
				removeInactiveCoders(coders, time);
				System.out.println(coders.size());
				rankCoders(coders, roundId, updateRankHistory);
			}
			rs.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private void removeInactiveCoders(Map coders, long time) {
		long diff = 1000L * 60L * 60L * 24L * 180L;
		for (Iterator i = coders.values().iterator(); i.hasNext();) {
			Coder coder = (Coder)i.next();
			if (time - coder.lastRatedTime > diff) {
				i.remove();
			}
		}
	}
	
	private void rankCoders(Map coders, int roundId, PreparedStatement ps) throws SQLException{
		List list = new ArrayList(coders.values());
		Collections.sort(list);
		
		int rank = 0;
		int count = list.size();
		int previousRating = Integer.MIN_VALUE;
		for (Iterator i = list.iterator(); i.hasNext();) {
			Coder coder = (Coder)i.next();
			int rating = coder.rating;
			long coderId = coder.coderId;
			
			if (rating != previousRating) {
				rank++;
				previousRating = rating;
			}
			
            ps.setLong(1, coderId);
            ps.setInt(2, roundId);
            ps.setFloat(3, (float) 100 * ((float) (count - rank) / count));
            ps.setInt(4, rank);
//            ps.executeUpdate();
            
            System.out.println(rank + "\t" + roundId + "\t" + coderId);
		}
	}
	
	private static class Coder {
		long coderId;
		int rating;
		long lastRatedTime;
		
        public int compareTo(Object object) {
            if (((Coder)object).rating > rating) {
                return 1;
            }
            else if (((Coder)object).rating < rating) {
                return -1;
            }
            else {
                return 0;
            }
        }
	}

}
