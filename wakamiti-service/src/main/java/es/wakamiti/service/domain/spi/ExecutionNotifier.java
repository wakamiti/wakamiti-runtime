/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.spi;


public interface ExecutionNotifier<T> {

    void notify(
            Integer status
    );

    void addObserver(
            T observer
    );

    void removeObserver(
            T observer
    );

}
