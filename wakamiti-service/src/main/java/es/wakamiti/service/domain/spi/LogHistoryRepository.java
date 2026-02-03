/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.spi;


import java.util.List;


public interface LogHistoryRepository {

    void save(
            String message
    );

    List<String> find();

    void clear();

    int size();

}
