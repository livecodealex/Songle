package globals;


import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import com.songle.s1505883.songle.R;

import org.json.JSONException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import database.AppDatabase;
import datastructures.GuessedWords;
import datastructures.LocationDescriptor;
import datastructures.MarkerDescriptor;
import datastructures.Placemarks;
import datastructures.SongDescriptor;
import datastructures.SongLyricsDescriptor;
import tools.Algorithm;
import tools.DebugMessager;
import tools.SongListParser;
import tools.SongLyricsParser;
import tools.WordLocationParser;

public class GlobalLambdas
{

    // A lambda for getting a stream, given a url
    public static final Function<URL, InputStream> get_stream = (url) -> {
        try
        {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn . setReadTimeout(10000);
            conn . setConnectTimeout(15000);
            conn . setRequestMethod("GET");
            conn . setDoInput(true);

            conn . connect();
            return conn . getInputStream();
        }
        catch(Exception e)
        {
            return null;
        }
    };

    // insert songs into database
    public static final BiConsumer<AppDatabase, List<SongDescriptor>> insertSongsConsumer =
            (db, l) ->
            {
                l.forEach(x ->
                {
                    try
                    {
                        db.songDao().insertSong(x);
                    }
                    catch (android.database.sqlite.SQLiteConstraintException e)
                    {
                        // not worth it to see if it exists beforehand
                        DebugMessager.getInstance().error(
                                "Trying to insert duplicate key " +
                                        x.getNumber() +
                                        " into database. Skipping"
                        );
                    }
                    catch (Exception e)
                    {
                        throw e;
                    }
                });
            };

    // download and check inital things
    public static final BiConsumer<Context, InputStream> initialCheckAndDownload = (ctxt, inputStream) ->
    {
        // we know that this will not run on the UI thread
        try
        {
            List<SongDescriptor> r_value = new ArrayList<>();

            SharedPreferences prefs = ctxt.getSharedPreferences(
                    ctxt.getString(
                            R.string.shared_prefs_key
                    ),
                    Context.MODE_PRIVATE
            );

            String existingTimestamp =
                    prefs.getString(
                            ctxt.getString(
                                    R.string.timestamp_key
                            ),
                            null
                    );

            String new_timestamp = SongListParser.parse(inputStream, r_value, existingTimestamp);

            if (new_timestamp == null)
            {
                return;
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(
                    ctxt . getString(R.string.timestamp_key),
                    new_timestamp
            );
            editor.commit();

            insertSongsConsumer.accept(
                    AppDatabase.getAppDatabase(ctxt),
                    r_value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    };


    public static final Function<AppDatabase, List<SongDescriptor>> getGuessedDescriptors =
            db -> db .songDao() . getGuessedSongs();

    public static final Function<AppDatabase, List<SongDescriptor>> getAllDescriptors =
            db -> db . songDao() . getAll();

    public static final Function<InputStream, SongLyricsDescriptor> getLyrics = is -> {
        try
        {
            return SongLyricsParser.parseLyrics(is);
        }
        catch (IOException e)
        {
            e . printStackTrace();
            return null;
        }
    };

    public static final Algorithm.Functional.TriFunction<Integer, Integer, SongLyricsDescriptor, BiConsumer<Context, InputStream>> getMaps = (lvl, id, des) -> (ctxt, is) -> {
        List<LocationDescriptor> r_value_1 = new ArrayList<>();
        List<MarkerDescriptor> r_value_2 = new ArrayList<>();
        AppDatabase db = AppDatabase.getAppDatabase(ctxt);

        try
        {
            // parse the placemarjs
            WordLocationParser.parse(
                    is,
                    des,
                    r_value_1,
                    r_value_2,
                    lvl,
                    id
            );


            // insert locations into DB
            r_value_1 . forEach(x ->
            {
                try
                {
                    db.locationDao().insertLocations(x);
                }
                catch (android.database.sqlite.SQLiteConstraintException e)
                {
                    DebugMessager.getInstance().error(
                            "Trying to insert duplicate key (" +
                                    "coords: " + x.getCoordinates() + ", " +
                                    "word: " + x.getWord() + ", " +
                                    "category :" + x.getCategory() + ", " +
                                    "map_number :" + x.getMap_number() + ", " +
                                    "song_id :" + x . getSongId() + ")" +
                                    " into database. Skipping"
                    );
                }
                catch(Exception e)
                {
                    throw e;
                }
            });

            // insert makers into DB
            r_value_2 . forEach(x ->
            {
                try
                {
                    db.markerDao().insertMarkers(x);
                }
                catch (android.database.sqlite.SQLiteConstraintException e)
                {
                    DebugMessager.getInstance().error(
                            "Trying to insert duplicate key " +
                                    x.getCategory() +
                                    " into database. Skipping"
                    );
                }
                catch(Exception e)
                {
                    throw e;
                }
            });
        }
        catch (IOException|XmlPullParserException e)
        {
            e . printStackTrace();
        }
        catch (Exception e)
        {
            throw e;
        }
    };

    // get placemarks
    public final static BiFunction<Integer, Integer, Function<AppDatabase, Placemarks>> plm = (lvl, id) -> db ->
        new Placemarks(
                db . locationDao() . getUndiscoveredActiveLocations(lvl, id),
                db . markerDao() . getAllMarkers()
        );

    // get words grouped by category
    public final static Function<Integer, Function<AppDatabase, List<GuessedWords>>> gw = s_id ->
            db -> {
                List<LocationDescriptor> locs =  db . locationDao() . getGuessedWords(s_id);
                Map<String, List<String>> grouped = Algorithm.Collections.groupBy(
                        locs,
                        LocationDescriptor::getCategory,
                        LocationDescriptor::getWord
                );

                return grouped . keySet() . stream() . map(
                        key -> new GuessedWords(key, grouped.get(key))
                ).collect(Collectors.toList());
    };
}
