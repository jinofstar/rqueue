/*
 * Copyright 2020 Sonu Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.sonus21.rqueue.models.response;

import com.github.sonus21.rqueue.models.SerializableBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@Setter
@SuppressWarnings("java:S2160")
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TableRow extends SerializableBase {
  private static final long serialVersionUID = -1493539297572507490L;
  private List<TableColumn> columns = new ArrayList<>();
  private List<RowColumnMeta> meta;

  public TableRow(TableColumn column) {
    this(Collections.singletonList(column), null);
  }

  public TableRow(List<TableColumn> columns) {
    this(columns, null);
  }

  public void addColumn(TableColumn column) {
    this.columns.add(column);
  }
}
