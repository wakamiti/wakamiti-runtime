/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.config;


import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.microprofile.tyrus.TyrusUpgradeProvider;


/**
 * Fixes an issue in class {@link TyrusUpgradeProvider} by adding {@link Weight}
 * annotation.
 * It is necessary for the Helidon ServiceLoader to use the Tyrus configuration.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 100)
@SuppressWarnings("deprecation")
public class FixedTyrusUpgradeProvider extends TyrusUpgradeProvider {

}