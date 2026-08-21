package org.pixelfire.nationwars.settlement;

import net.minecraftforge.registries.RegistryObject;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.state.PeaceClause;

public final class NationWarsPeaceClauses
{
    public static final RegistryObject<PeaceClause> TRANSFER_CITY =
            NationWarsMod.PEACE_CLAUSES.register("transfer_city", TransferCityClause::new);
    public static final RegistryObject<PeaceClause> RELEASE_OCCUPATION =
            NationWarsMod.PEACE_CLAUSES.register("release_occupation", ReleaseOccupationClause::new);
    public static final RegistryObject<PeaceClause> TRIBUTE =
            NationWarsMod.PEACE_CLAUSES.register("tribute", TributeClause::new);
    public static final RegistryObject<PeaceClause> CEASEFIRE =
            NationWarsMod.PEACE_CLAUSES.register("ceasefire", CeasefireClause::new);

    private NationWarsPeaceClauses()
    {
    }

    /** Forces this class to load (and its {@code RegistryObject}s to be created) before registration fires. */
    public static void bootstrap()
    {
    }
}
