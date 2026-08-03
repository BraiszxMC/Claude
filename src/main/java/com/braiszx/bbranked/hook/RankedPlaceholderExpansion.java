package com.braiszx.bbranked.hook;

import com.braiszx.bbranked.BlockBallRankedPlugin;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.LeaderboardType;
import com.braiszx.bbranked.data.TopEntry;
import com.braiszx.bbranked.data.PlayerStats;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.queue.QueueManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Placeholders para PlaceholderAPI.
 *
 * <ul>
 *   <li>%bbranked_elo%          Elo actual</li>
 *   <li>%bbranked_peak%         Elo maximo alcanzado</li>
 *   <li>%bbranked_rank%         nombre de la division (con colores)</li>
 *   <li>%bbranked_rank_id%      id de la division</li>
 *   <li>%bbranked_wins%         victorias</li>
 *   <li>%bbranked_losses%       derrotas</li>
 *   <li>%bbranked_draws%        empates</li>
 *   <li>%bbranked_matches%      partidas jugadas</li>
 *   <li>%bbranked_goals%        goles</li>
 *   <li>%bbranked_winrate%      porcentaje de victorias</li>
 *   <li>%bbranked_streak%       racha actual</li>
 *   <li>%bbranked_position%     posicion en el ranking</li>
 *   <li>%bbranked_in_queue%     si/no</li>
 *   <li>%bbranked_in_match%     si/no</li>
 * </ul>
 *
 * <p>Tablas de clasificacion, con el formato
 * {@code %bbranked_top_<tabla>_<name|value>_<puesto>%}:</p>
 *
 * <pre>
 *   %bbranked_top_elo_name_1%       %bbranked_top_elo_value_1%
 *   %bbranked_top_goals_name_1%     %bbranked_top_goals_value_1%
 *   %bbranked_top_wins_name_1%      %bbranked_top_wins_value_1%
 *   %bbranked_top_mvps_name_1%      %bbranked_top_mvps_value_1%
 *   %bbranked_top_matches_name_1%   %bbranked_top_matches_value_1%
 *   %bbranked_top_winrate_name_1%   %bbranked_top_winrate_value_1%
 *   %bbranked_top_streak_name_1%    %bbranked_top_peak_value_1%
 *   %bbranked_top_losses_...%       %bbranked_top_draws_...%
 *   %bbranked_top_leaves_...%
 * </pre>
 *
 * <p>Y el puesto propio en cada tabla con
 * {@code %bbranked_position_<tabla>%}, por ejemplo
 * {@code %bbranked_position_goals%}.</p>
 *
 * <p>Se mantienen {@code %bbranked_top_name_N%} y
 * {@code %bbranked_top_elo_N%} por compatibilidad: apuntan a la tabla de Elo.</p>
 */
public final class RankedPlaceholderExpansion extends PlaceholderExpansion {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BlockBallRankedPlugin plugin;
    private final RankedConfig config;
    private final StatsManager stats;
    private final QueueManager queues;

    public RankedPlaceholderExpansion(BlockBallRankedPlugin plugin, RankedConfig config,
                                      StatsManager stats, QueueManager queues) {
        this.plugin = plugin;
        this.config = config;
        this.stats = stats;
        this.queues = queues;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bbranked";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BraiszxMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.startsWith("top_")) {
            return topPlaceholder(key);
        }

        if (player == null) {
            return "";
        }

        // %bbranked_position_goals%, %bbranked_position_mvps%...
        if (key.startsWith("position_")) {
            LeaderboardType type = LeaderboardType.byId(key.substring("position_".length()));
            if (type == null) {
                return null;
            }
            int position = stats.positionIn(type, player.getUniqueId());
            return position > 0 ? String.valueOf(position) : "-";
        }

        PlayerStats found = stats.cached(player.getUniqueId());
        if (found == null) {
            // Solo se sirven datos cacheados: PlaceholderAPI se consulta muy a
            // menudo y no puede esperar a la base de datos.
            return switch (key) {
                case "elo", "peak" -> String.valueOf(config.startingElo());
                case "wins", "losses", "draws", "matches", "goals", "streak", "mvps" -> "0";
                case "winrate" -> "0.0";
                case "rank" -> legacy(config.rankOf(config.startingElo()).displayName());
                case "rank_id" -> config.rankOf(config.startingElo()).id();
                case "position" -> "-";
                case "in_queue", "in_match" -> "no";
                default -> null;
            };
        }

        return switch (key) {
            case "elo" -> String.valueOf(found.elo());
            case "peak" -> String.valueOf(found.peakElo());
            case "rank" -> legacy(config.rankOf(found.elo()).displayName());
            case "rank_id" -> config.rankOf(found.elo()).id();
            case "wins" -> String.valueOf(found.wins());
            case "losses" -> String.valueOf(found.losses());
            case "draws" -> String.valueOf(found.draws());
            case "matches" -> String.valueOf(found.matches());
            case "goals" -> String.valueOf(found.goals());
            case "mvps" -> String.valueOf(found.mvps());
            case "winrate" -> String.format(Locale.ROOT, "%.1f", found.winRate());
            case "streak" -> String.valueOf(found.winStreak());
            case "best_streak" -> String.valueOf(found.bestStreak());
            case "leaves" -> String.valueOf(found.leaves());
            case "position" -> {
                int position = stats.position(player.getUniqueId());
                yield position > 0 ? String.valueOf(position) : "-";
            }
            case "in_queue" -> queues.isQueued(player.getUniqueId()) ? "si" : "no";
            case "in_match" -> plugin.matchManager().isInMatch(player.getUniqueId()) ? "si" : "no";
            default -> null;
        };
    }

    /**
     * Resuelve los placeholders de tablas.
     *
     * <p>Formato general: {@code top_<tabla>_<name|value>_<puesto>}, por
     * ejemplo {@code top_goals_name_1}. Se mantienen tambien los antiguos
     * {@code top_name_N} y {@code top_elo_N}, que apuntan a la tabla de Elo.</p>
     */
    private String topPlaceholder(String key) {
        String rest = key.substring("top_".length());

        // Formato antiguo: top_name_N y top_elo_N.
        if (rest.startsWith("name_")) {
            return topValue(LeaderboardType.ELO, rest.substring("name_".length()), true);
        }
        if (rest.startsWith("elo_") && isNumber(rest.substring("elo_".length()))) {
            return topValue(LeaderboardType.ELO, rest.substring("elo_".length()), false);
        }

        int nameAt = rest.lastIndexOf("_name_");
        int valueAt = rest.lastIndexOf("_value_");
        boolean wantsName = nameAt >= 0;
        int split = wantsName ? nameAt : valueAt;
        if (split < 0) {
            return null;
        }

        LeaderboardType type = LeaderboardType.byId(rest.substring(0, split));
        if (type == null) {
            return null;
        }
        String rawIndex = rest.substring(split + (wantsName ? "_name_".length() : "_value_".length()));
        return topValue(type, rawIndex, wantsName);
    }

    private String topValue(LeaderboardType type, String rawIndex, boolean wantsName) {
        int index;
        try {
            index = Integer.parseInt(rawIndex);
        } catch (NumberFormatException exception) {
            return "";
        }

        List<TopEntry> entries = stats.top(type);
        if (index < 1 || index > entries.size()) {
            return wantsName ? "-" : "0";
        }
        TopEntry entry = entries.get(index - 1);
        return wantsName ? entry.name() : entry.display(type);
    }

    private static boolean isNumber(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * PlaceholderAPI devuelve texto plano, asi que el MiniMessage del config se
     * convierte a codigos de color clasicos.
     */
    private static String legacy(String miniMessage) {
        return LEGACY.serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
