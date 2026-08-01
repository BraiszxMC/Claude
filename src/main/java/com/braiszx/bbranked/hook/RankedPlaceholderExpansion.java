package com.braiszx.bbranked.hook;

import com.braiszx.bbranked.BlockBallRankedPlugin;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.LeaderboardEntry;
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
 *   <li>%bbranked_top_name_N%   nombre del puesto N</li>
 *   <li>%bbranked_top_elo_N%    Elo del puesto N</li>
 * </ul>
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

        if (key.startsWith("top_name_") || key.startsWith("top_elo_")) {
            return topPlaceholder(key);
        }

        if (player == null) {
            return "";
        }

        PlayerStats found = stats.cached(player.getUniqueId());
        if (found == null) {
            // Solo se sirven datos cacheados: PlaceholderAPI se consulta muy a
            // menudo y no puede esperar a la base de datos.
            return switch (key) {
                case "elo", "peak" -> String.valueOf(config.startingElo());
                case "wins", "losses", "draws", "matches", "goals", "streak" -> "0";
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

    private String topPlaceholder(String key) {
        boolean wantsName = key.startsWith("top_name_");
        String rawIndex = key.substring(wantsName ? "top_name_".length() : "top_elo_".length());

        int index;
        try {
            index = Integer.parseInt(rawIndex);
        } catch (NumberFormatException exception) {
            return "";
        }

        List<LeaderboardEntry> entries = stats.leaderboard();
        if (index < 1 || index > entries.size()) {
            return wantsName ? "-" : "0";
        }
        LeaderboardEntry entry = entries.get(index - 1);
        return wantsName ? entry.name() : String.valueOf(entry.elo());
    }

    /**
     * PlaceholderAPI devuelve texto plano, asi que el MiniMessage del config se
     * convierte a codigos de color clasicos.
     */
    private static String legacy(String miniMessage) {
        return LEGACY.serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
